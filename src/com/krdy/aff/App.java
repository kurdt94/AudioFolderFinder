package com.krdy.aff;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

// example run command
// -i d:\Downloads\ -o test -v

public class App {

	public enum AnsiColor {
	    RESET("\u001B[0m"),
	    RED("\u001B[31m"),
	    GREEN("\u001B[32m"),
	    YELLOW("\u001B[33m"),
	    BLUE("\u001B[34m"),
	    BOLD("\u001B[1m"),
	    UNDERLINE("\u001B[4m");

	    private final String code;

	    AnsiColor(String code) {
	        this.code = code;
	    }

	    public String getCode() {
	        return code;
	    }

	    @Override
	    public String toString() {
	       return this.code;
	    }
	}
	
	public static class Args {
        @Parameter(names = {"-i", "--input"}, description = "Input file path")
        public String inputFile;

        @Parameter(names = {"-o", "--output"}, description = "Output filename")
        public String outputFile;

        @Parameter(names = {"-v", "--verbose"}, description = "Enable verbose mode")
        public boolean verbose = false;
    }
	
	
	private static final List<String> audioFileExtensions = List.of(".flac", ".wav", ".mp3");

	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
   
    public static void main(String[] args) throws IOException, InterruptedException {
    	    	
    	printStartup();
    	boolean verboseMode = false;
        String rootDirectory = null;
        String outPutFileName = "output";

        Args arguments = new Args();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        
        // Require inputFile
        if(arguments.inputFile == null) {
            LOGGER.warn(AnsiColor.RED+"Please provide a target directory."+AnsiColor.RESET);
            return;
        }else {
            rootDirectory = arguments.inputFile;	
            LOGGER.info(AnsiColor.YELLOW+"Using Input Folder: " + arguments.inputFile+AnsiColor.RESET);
        }
        
        // Optional outputFile
		if(arguments.outputFile != null){ 
			outPutFileName = arguments.outputFile;
			LOGGER.info(AnsiColor.YELLOW+"Using custom Output File Name: " + arguments.outputFile+AnsiColor.RESET);
		}else {
			LOGGER.info(AnsiColor.YELLOW+"Using default Output File Name: " + outPutFileName+AnsiColor.RESET);
		}
		
		// Optional verbose mode
        if(arguments.verbose){
        	 verboseMode = true;
        	 LOGGER.info(AnsiColor.YELLOW+"Verbose mode enabled"+AnsiColor.RESET);
        }
        
        processFolder(rootDirectory, outPutFileName,verboseMode);
        
    }
    
    public static void processFolder(String root, String output, boolean verboseMode) {
    	// Set our Starting (root) Directory and output file
    	String id = getShortString();
    	String outputFileName = output + id + ".txt";
    	    	
    	Path rootPath = Paths.get(root); 
        Path outputFile = Paths.get(outputFileName);  

    	LOGGER.info(AnsiColor.GREEN+"Output file :  " + outputFile.toString()+AnsiColor.RESET);
        
        if (!Files.isDirectory(rootPath)) {
        	LOGGER.warn(AnsiColor.RED+"The provided path is not a directory, please provide a valid path"+AnsiColor.RESET);
            return;
        }
        
        // walk the rootPath and add found Directories to the ArrayList
        // it does not follow symbolic links, and visits all levels of the file tree.
        List<Path> directories = new ArrayList<>();
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if(safeIsDirectory(dir)) {
                         directories.add(dir);
                         return FileVisitResult.CONTINUE;
                    } else {
                       return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                	if(verboseMode) {
                	 LOGGER.warn("Error accessing path: " + file + " , skipping: " + exc.getMessage());
                	}
                     return FileVisitResult.CONTINUE; // Ignore and continue the walk
                }
                
                @Override
                 public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                     return FileVisitResult.CONTINUE;
                 }
           });


        } catch (Exception e) {
            LOGGER.error(AnsiColor.RED+"Error walking directory"+AnsiColor.RESET, e);
        }

        LOGGER.info(AnsiColor.GREEN+"Found " + directories.size() + " directories"+AnsiColor.RESET);
        
        // 
        try {
        	LOGGER.info(AnsiColor.YELLOW+"Looking for Audio Folders in " + directories.size() + " directories."+AnsiColor.RESET);
			var result = processDirectories(directories);
			try {
				LOGGER.info(AnsiColor.YELLOW+"Writing results to file .."+AnsiColor.RESET);
				writeResultsToFile(result.audioInfo(), outputFile, result.totalAudioFiles());
			} catch (IOException e) {
				//e.printStackTrace();
				LOGGER.warn(AnsiColor.RED+"Failed to write " + outputFile + " to disk."+AnsiColor.RESET, e.getCause());
			}
		} catch (InterruptedException e) {
			//e.printStackTrace();
		}
        
        LOGGER.info(AnsiColor.GREEN+"Success, a list of Audio Folders has been written to: "+AnsiColor.RESET + outputFile.toString());
        
    }
    
    private static boolean safeIsDirectory(Path path) {
        try {
            return Files.isDirectory(path);
        } catch(Exception e) {
           LOGGER.warn(AnsiColor.RED+"Failed to check if path: " + path + " is a directory"+AnsiColor.RESET, e);
           return false;
        }
    }

    // Processes a List of (Path) Directories and returns a ProcessingResults Object
    private static ProcessingResults processDirectories(List<Path> directories) throws InterruptedException {
    	List<FolderInfo> folderInfoList = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger totalAudioFiles = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (Path dir : directories) {
            executor.submit(() -> {
                //System.out.println("processing directory: " + dir.toString());
                var folderInfo = getFolderInfo(dir);

                if (folderInfo.audioFilesCount() > 0) {
                    folderInfoList.add(folderInfo);
                    totalAudioFiles.addAndGet(folderInfo.audioFilesCount());
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // Sort the results by path
        folderInfoList.sort(Comparator.comparing(FolderInfo::path));

        return new ProcessingResults(folderInfoList, totalAudioFiles.get());
    }

    // Returns a FolderInfo Object containing path, audioFilesCount and size
    private static FolderInfo getFolderInfo(Path directory) {
        int audioFileCount = 0;
        long folderSize = 0;

        try {
            audioFileCount = (int) Files.list(directory)
                    .filter(file -> {
                        String fileName = file.getFileName().toString().toLowerCase();
                        return audioFileExtensions.stream().anyMatch(ext -> fileName.endsWith(ext));
                    }).count();

            folderSize = calculateFolderSize(directory);

        } catch (IOException e) {
            LOGGER.error(AnsiColor.RED+"Could not access direcory : " + directory + " "+ AnsiColor.RESET + e.getMessage());
        }
        return new FolderInfo(directory.toString(), audioFileCount, folderSize);

    }
    
    // Calculate total size of the given (Path) Directory , returns Long
    private static long calculateFolderSize(Path directory) throws IOException {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            LOGGER.error(AnsiColor.RED+"Error calculating file size " + AnsiColor.RESET + file.toString() + " ", e);
                            return 0L; // Return 0 if we can't get the file's size.
                        }
                    })
                    .sum();
        } catch (IOException e) {
            LOGGER.error(AnsiColor.RED+"error getting folder size " + AnsiColor.RESET + directory.toString() + " ", e);
            return 0L; // Return 0 if we can't get the folder's size.
        }
    }

    private static void writeResultsToFile(List<FolderInfo> folderInfo, Path outputFile, int totalAudioFiles) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (var info : folderInfo) {
                writer.write("Path: " + info.path() + ", ");
                writer.write("Audio Files: " + info.audioFilesCount() + ", ");
                writer.write("Size (MB): " + formatSizeMB(info.size()));
                writer.newLine();
            }
            writer.newLine();
            writer.write("Total audio files found: " + totalAudioFiles);
        }
    }

    // format size to MB
    private static String formatSizeMB(long bytes) {
        double sizeInMB = (double) bytes / (1024 * 1024);
        return String.format("%.2f", sizeInMB);
    }

    // returns a shortened UUID
    public static String getShortString() {
    	
    	try {
    		return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                    .substring(0, 5);
		} catch (Exception e) {
			LOGGER.warn(AnsiColor.RED+"Error encoding UUID: " + AnsiColor.RESET + e.getMessage());
			return generateRandomString();
		} 
    	
    }

    // Generate a random alphanumeric string as a fallback for getShortString()
    private static String generateRandomString() {
        
    	Random RANDOM = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            int randomCharIndex = RANDOM.nextInt(36); // 0-9, a-z
            if(randomCharIndex < 10) {
                sb.append((char)('0' + randomCharIndex));
            } else {
                sb.append((char)('a' + (randomCharIndex - 10)));
            }
        }
        return sb.toString();
    }
    
    
    private record ProcessingResults(List<FolderInfo> audioInfo, int totalAudioFiles) { }

    private record FolderInfo(String path, int audioFilesCount, long size) { }
    
    private static void printStartup() {
    
	System.out.print(
			AnsiColor.GREEN+"""
			................................
			.##..##..#####...#####...##..##.
			.##.##...##..##..##..##...####..
			.####....#####...##..##....##...
			.##.##...##..##..##..##....##...
			.##..##..##..##..#####.....##...
			................................
			:: AudioFolderFinder 1.1.0.0  ::
			................................
			:: - Copyright 2024           :: 
			:: - Erwin Graanstra          ::
			:: - krdy.online@gmail.com    ::
			................................
			"""+AnsiColor.RESET
			);
    	
    }

}
