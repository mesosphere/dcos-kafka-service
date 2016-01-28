#!/usr/bin/python

###
# Uses the 'md-to-toc' python script to generate and insert tables of contents for *.md
###

import codecs, os, os.path, sys, tempfile

try:
    from urllib.request import urlopen # python 3
except ImportError:
    from urllib2 import urlopen # python 2

TOC_GEN_FILENAME = "md-to-toc.py"
TOC_GEN_URL = "https://raw.githubusercontent.com/amaiorano/md-to-toc/master/" + TOC_GEN_FILENAME

MARKDOWN_FILE_ENDSWITH = ".md"
TOC_MARKER_START = "TOC START"
TOC_MARKER_END = "TOC END"

def download(url):
    print("Downloading %s..." % url)
    r = urlopen(url)
    if r.code == 200:
        return r.read().decode("utf-8");
    else:
        raise "Got error=%d when downloading %s" % (r.code, url)

def get_parser_module():
    if not os.path.isfile(TOC_GEN_FILENAME):
        with open(TOC_GEN_FILENAME, 'w') as module_out:
            module_out.write(download(TOC_GEN_URL))
            module_out.close()
        print("Created %s" % TOC_GEN_FILENAME)
    return __import__(TOC_GEN_FILENAME.split(".")[0])

class Writer:
    def __init__(self):
        self.__chunks = []

    def write(self, chunk):
        self.__chunks.append(chunk)

    def flush(self):
        pass

    def get(self):
        return "".join(self.__chunks)

def print_toc(parser, filepath):
    parser.main([sys.argv[0], filepath])

def insert_toc(parser, filepath):
    # Create temp file which only contains content following the TOC
    after_toc = False
    tmpfilepath = tempfile.mkstemp()[1]
    with codecs.open(tmpfilepath, 'w', "utf-8-sig") as tmpfile:
        for line in open(filepath).readlines():
            if line.find(TOC_MARKER_END) >= 0:
                after_toc = True
                continue
            if after_toc:
                tmpfile.write(line + "\n")
        tmpfile.close()

    # Hack: Intercept stdout produced by the parser
    original_stdout = sys.stdout
    parsed_toc = Writer()
    sys.stdout = parsed_toc
    print_toc(parser, tmpfilepath)
    sys.stdout = original_stdout

    new_lines = []
    inside_toc = False
    for line in open(filepath).readlines():
        if line.find(TOC_MARKER_START) >= 0:
            new_lines.append(line)
            new_lines.append("\n")
            new_lines.append(parsed_toc.get())
            new_lines.append("\n")
            inside_toc = True
        if line.find(TOC_MARKER_END) >= 0:
            inside_toc = False
        if not inside_toc:
            new_lines.append(line)
    return new_lines

def update_mds_in_script_dir(parser):
    script_dir = os.path.dirname(os.path.realpath(__file__))
    os.chdir(script_dir)
    print("Scanning %s:" % os.getcwd())
    for filename in os.listdir(script_dir):
        if not filename.endswith(MARKDOWN_FILE_ENDSWITH):
            continue
        print("  Updating %s" % filename)
        new_lines = insert_toc(parser, filename)
        open(filename, 'w').write("".join(new_lines))

if __name__ == "__main__":
    if len(sys.argv) <= 1:
        # Inject the TOC into each .md file in this script's directory
        update_mds_in_script_dir(get_parser_module())
        sys.exit(0)

    if sys.argv[1] == "--help" or sys.argv[1] == "-h":
        print("Syntax: %s [path or url]" % sys.argv[0])
        print("  If path or url is provided: prints the TOC for that path or url")
        print("  If no args: injects the TOC for each .md file in the script's directory")
        sys.exit(1)

    # Print the TOC for the provided file or URL
    parser = get_parser_module()
    if os.path.isfile(sys.argv[1]):
        print_toc(parser, sys.argv[1])
    else:
        # Not an existent file, treat as a URL
        tmpfilepath = tempfile.mkstemp()[1]
        with codecs.open(tmpfilepath, 'w', "utf-8-sig") as tmpfile:
            tmpfile.write(download(sys.argv[1]))
            tmpfile.close()
        print_toc(parser, tmpfilepath)
        os.unlink(tmpfilepath)
