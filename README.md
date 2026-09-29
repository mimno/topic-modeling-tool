[![DOI](https://zenodo.org/badge/47996186.svg)](https://zenodo.org/badge/latestdoi/47996186)

# Enderle Topic Modeling Tool

A point-and-click program for topic modeling a folder of text files, built on
[MALLET](https://mimno.github.io/Mallet/)'s implementation of latent Dirichlet
allocation (LDA). Your files never leave your computer.

This is the maintained continuation of
[Scott Enderle's Topic Modeling Tool](https://github.com/senderle/topic-modeling-tool).
See [Acknowledgements](#acknowledgements).

## Install

Download the installer for your computer from the
[Releases](https://github.com/mimno/topic-modeling-tool/releases) page. Java is
included, so you don't need to install it separately.

* **Mac (Apple Silicon: M1 and later):** `Topic Modeling Tool-<version>-mac-arm64.dmg`
* **Mac (Intel):** `Topic Modeling Tool-<version>-mac-x64.dmg`
* **Windows:** `Topic Modeling Tool-<version>.msi`
* **Linux (Debian/Ubuntu):** the `.deb` file
* **Anywhere with Java 17 or later:** `TopicModelingTool.jar` (run with
  `java -jar TopicModelingTool.jar`)

On a Mac, open the `.dmg` and drag the app into your Applications folder.

## Use

1. **Input folder:** choose a folder of plain-text (`.txt`) files. Files in
   subfolders are included too. Convert PDFs and Word files to plain text first.
2. **Output folder:** where results are saved. By default this is
   `Documents/Topic Modeling Tool`, and each run gets its own folder named
   after the input folder plus the date and time.
3. **Number of topics:** 10–20 is a good start for a few hundred documents.
4. Click **Learn Topics**, then **Open Results** to browse the topics in your
   web browser.

**Document length matters.** Topic models work best on passages of a
paragraph or a few pages, not whole books. So by default, files longer than
500 words are split into 500-word passages, named `moby-dick-1.txt`,
`moby-dick-2.txt`, and so on; shorter files are kept whole. To change the
passage length, or to keep every file whole, use **Optional Settings → Split
files into chunks of** (0 turns splitting off).

The tool splits words on spaces and punctuation, so languages written without
spaces between words (such as Chinese or Japanese) need to be segmented into
words before modeling.

### Output

| Folder | Contents |
|--------|----------|
| `output_html/` | Web pages: `index.html` lists topics; each topic page shows its top words, related topics and top documents; each document page shows its topics and an excerpt. |
| `output_csv/topic-words.csv` | Top words for each topic. |
| `output_csv/topics-metadata.csv` | One row per document: metadata columns (if any), then the proportion of each topic. Good for spreadsheets. |
| `output_csv/topics-in-docs.csv` | Each document's topics, largest first, as topic/proportion pairs. |
| `output_csv/docs-in-topics.csv` | The top 500 documents for each topic. |
| `output_mallet/` | Raw MALLET files (only if *Also save raw MALLET files* is checked). |

### Metadata

Optionally, choose a CSV file in **Optional Settings → Metadata file**. The first
column must be the file name (for example `letter-1851-03-04.txt`); the other
columns are copied into `topics-metadata.csv` and shown on each document page.

## Build from source

Requires JDK 17 or later and [Maven](https://maven.apache.org/).

```
cd TopicModelingTool
mvn package                 # runs the tests, then writes target/TopicModelingTool.jar
java -jar target/TopicModelingTool.jar
./package-app.sh            # native installer in target/dist (needs JDK 17+ jpackage)
```

`package-app.sh` builds a `.dmg` on macOS, an `.msi` on Windows (requires the
[WiX toolset](https://wixtoolset.org/)), and a `.deb` on Linux. GitHub Actions
builds all of them on every push; tagging a commit `v*` publishes a release.

## Reporting bugs

Please [open an issue](https://github.com/mimno/topic-modeling-tool/issues) and
include your operating system, the tool's version, and everything shown in the
Console box.

---

#### Acknowledgements<a name="acknowledgements"></a>

This tool is a fork of Scott Enderle's Topic Model Tool, which was itself forked 
from a version built by David Newman and Arun Balagopalan. Scott added metadata support,
automatic file segmentation, hyperparameter optimization, custom tokenization
and multicore training. He also wrote the documentation. This fork is dedicated to
his memory. I hope that by keeping this application running I can make it a lasting
tribute to his work.

Previous work on the GUI for MALLET has been supported by a National Leadership
Grant (LG-06-08-0057-08) from the Institute of Museum and Library Services to
Yale University, the University of Michigan, and the University of California,
Irvine. Work on Scott Enderle's version benefited from the support of
[Penn Libraries](http://www.library.upenn.edu/) and the University of
Pennsylvania's [Price Lab for Digital Humanities](https://pricelab.sas.upenn.edu/).
