if (args.length != 2) {
    println "MissingFiles srcDirectory dstDirectory"
    return
}

class FileScan {
    FileScan(File file) {
        this.fileName = file.name
        this.size = file.length()
        this.path = file.parent
    }

    String fileName
    String path // path without file name
    Long size
    boolean found
}

class DiffTrie {

    private static class DiffTrieNode {
        DiffTrieNode(String[] path, FileScan value = null) {
            this.path = path
            this.value = value
        }

        String[] path   // path including file name
        Map<String, DiffTrieNode> next = new HashMap<>()
        FileScan value

        String toString(String indent) {
            if (value) {
                return value.fileName + " is missing"
            } else {
                def ret = File.separator + path.join(File.separator) + ':\n'
                next.each { key, val ->
                    ret += indent + val.toString(indent + indent) + '\n'
                }
                return ret
            }
        }
    }

    DiffTrieNode root = new DiffTrieNode([] as String[])

    void put(FileScan value) {
        String[] valuePath = value.path.split(File.separator)
        valuePath = valuePath[1..valuePath.length - 1]
        DiffTrieNode rootNodeParent
        DiffTrieNode rootNode = root
        int i = 0
        List<String> ancestorPath = []
        while (true) {
            int j = i
            while (i < valuePath.length && i - j < rootNode.path.length && rootNode.path[i - j] == valuePath[i]) {
                ancestorPath << rootNode.path[i - j]
                i++
            }
            if (i < valuePath.length && rootNode.path.length == i - j && rootNode.next[valuePath[i]]) {
                rootNodeParent = rootNode
                rootNode = rootNode.next[valuePath[i]]
                ancestorPath = []
            } else {
                break;
            }
        }


        List<String> splitValuePath = i < valuePath.length ? (i..valuePath.length - 1).collect {
            valuePath[it]
        } : []
        splitValuePath << value.fileName

        if (rootNode.path == (ancestorPath as String[]) && rootNode != root) {
            rootNode.next[splitValuePath[0]] = new DiffTrieNode(splitValuePath as String[], value)
        } else {
            if (rootNodeParent) {
                List<String> splitRootPath = (ancestorPath.size()..rootNode.path.length - 1).collect {
                    rootNode.path[it]
                }
                DiffTrieNode ancestor = new DiffTrieNode(ancestorPath as String[])
                ancestor.next[splitRootPath[0]] = rootNode
                rootNode.path = splitRootPath as String[]
                rootNodeParent.next[ancestorPath[0]] = ancestor
                rootNode = ancestor
            }

            rootNode.next[splitValuePath[0]] = new DiffTrieNode(splitValuePath as String[], value)


        }
    }

    boolean isEmpty() {
        return root.next.isEmpty()
    }

    String toString() {
        return root.next.collect { key, value -> value.toString('    ') }.join('\n')
    }
}

def srcDir = new File(args[0])
assert srcDir.isDirectory()
def dstDir = new File(args[1])
assert dstDir.isDirectory()


def diffTrie = new DiffTrie()
def fileNameMap = new HashMap<String, FileScan>()
def dirQueue = new LinkedList<File>()
dirQueue.add(srcDir)

while (!dirQueue.empty) {
    File dir = dirQueue.removeFirst()
    dir.eachFile { file ->
        if (file.isDirectory()) {
            dirQueue.add(file)
        } else {
            fileNameMap[file.name] = new FileScan(file)
        }
    }
}

dirQueue.add(dstDir)
while (!dirQueue.empty) {
    File dir = dirQueue.removeFirst()
    dir.eachFile { file ->
        if (file.directory) {
            dirQueue.add(file)
        } else if (fileNameMap[file.name] && fileNameMap[file.name].size == file.length()) {
            fileNameMap[file.name].found = true
        }
    }
}

fileNameMap.findAll { key, value -> !value.found }.each { key, value -> diffTrie.put(value) }

if (diffTrie.empty) {
    println "everything is copied"
} else {
    println diffTrie
}