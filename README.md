# AudioFolderFinder

A command-line tool to quickly identify and list directories containing audio files within a given directory.

## Overview

`AudioFolderFinder` is a Java-based utility designed to recursively scan a directory tree and locate folders containing audio files. It provides a summary of these folders, including the number of audio files found and the total size of the audio files within them. The output is saved to a text file for easy reference.

## Features

- **Recursive Directory Scanning:** Traverses all subdirectories within the specified input directory.
- **Audio File Detection:** Identifies audio files based on common file extensions (.flac, .wav, .mp3).
- **Folder Summary:** Reports the path, number of audio files, and total size of audio files for each directory containing at least one audio file.
- **Output to File:** Saves the scan results to a text file, including a total count of audio files found.
- **Command-Line Interface:** Utilizes command-line arguments for input and output configurations.
- **Verbose Mode:** Provides detailed output during the scanning process for debugging.
- **Concurrency:** Uses virtual threads to process directories in parallel for faster performance.
- **Colored Output:** Enhances readability with colored console messages (using ANSI escape codes).
- **Cross Platform:** Runs on any system with a Java Virtual Machine (JVM).

## Usage

To use `AudioFolderFinder`, you will need to execute it from the command line using the following format:

```bash
java -jar AudioFolderFinder.jar -i <input_directory> [-o <output_filename>] [-v]

-i, --input <input_directory>: (Required) The path to the root directory you want to scan for audio files.
-o, --output <output_filename>: (Optional) The base name for the output file (default is output). The tool will append a unique identifier and the .txt extension to this name.
-v, --verbose: (Optional) Enables verbose output for more detailed logging.

example: 
java -jar AudioFolderFinder.jar -i d:\Downloads\ -o my_audio_report
```

## Dependencies
The project uses the following dependencies:

- JCommander - For parsing command-line arguments.
- SLF4j - For logging.

## Contributing
Feel free to fork the project, make changes, and submit a pull request. All contributions are welcome!

## License
This project is licensed under the MIT License - see the LICENSE file for details.

## Author
Erwin Graanstra - krdy.online@gmail.com



