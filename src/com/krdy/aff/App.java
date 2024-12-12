package com.krdy.aff;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public class App {

	private static final List<String> audioFileExtensions = List.of(".flac", ".wav", ".mp3");

	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
   
    public static void main(String[] args) throws IOException, InterruptedException {
    	
    	System.out.println("capture this");
    	
    	printStartup();
    	
        String rootDirectory;
        String outPutFileName;
        
        // Argumens
        if (args.length == 0) {
            LOGGER.info("No command-line arguments provided.");
            
            rootDirectory = "f:\\_ AUDIO - BOOTLEG\\";
            outPutFileName = "output";
            
        } else {
        	
            LOGGER.info("Command-line arguments provided:");
            for (int i = 0; i < args.length; i++) {
                System.out.println("Argument " + i + ": " + args[i]);
            }
            
    		rootDirectory = args[0];
            outPutFileName = "output";
            
        }

        processFolder(rootDirectory, outPutFileName);
        
    }
    
    public static void processFolder(String root, String output) {
    	// Set our Starting (root) Directory and output file
    	String id = getShortString();
    	String outputFileName = output + id + ".txt";
    	    	
    	Path rootPath = Paths.get(root); 
        Path outputFile = Paths.get(outputFileName);  
        
    	LOGGER.info("Processing folder :  " + rootPath.toString());
    	LOGGER.info("Output file :  " + outputFile.toString());
        
        if (!Files.isDirectory(rootPath)) {
        	LOGGER.info("The provided path is not a directory, please provide a valid path");
            return;
        }
        
        // walk the rootPath and add all directorys
        List<Path> directories = new ArrayList<>();
        try {
            Files.walk(rootPath).filter(Files::isDirectory).forEach(directories::add);
        } catch (Exception e) {
            LOGGER.error("There was an error finding the folders :", e.getMessage());
        }
        
        LOGGER.info("found " + directories.size() + " directories");
          
        try {
			var result = processDirectories(directories);
			try {
				writeResultsToFile(result.audioInfo(), outputFile, result.totalAudioFiles());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        LOGGER.info("Audio containing directory paths have been written to: " + outputFile.toString());
        
    }
    
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

        // Sort the results alphabetically by path
        folderInfoList.sort(Comparator.comparing(FolderInfo::path));

        return new ProcessingResults(folderInfoList, totalAudioFiles.get());
    }

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
            LOGGER.error("Could not access direcory : " + directory + " " + e.getMessage());
        }
        return new FolderInfo(directory.toString(), audioFileCount, folderSize);

    }

    private static long calculateFolderSize(Path directory) throws IOException {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            LOGGER.error("Error calculating file size " + file.toString() + " ", e);
                            return 0L; // Return 0 in case of an error getting a file's size.
                        }
                    })
                    .sum();
        } catch (IOException e) {
            LOGGER.error("error getting folder size " + directory.toString() + " ", e);
            return 0L; // Return 0 in case there is a problem calculating folder size.
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
    // @ TODO ... faster one ? ? 
    public static String getShortString() {
    	
    	try {
    		return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                    .substring(0, 5);
		} catch (Exception e) {
			System.err.println("Error encoding UUID: " + e.getMessage());
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
			"""
			................................
			.##..##..#####...#####...##..##.
			.##.##...##..##..##..##...####..
			.####....#####...##..##....##...
			.##.##...##..##..##..##....##...
			.##..##..##..##..#####.....##...
			................................
			:: AudioFolderFinder 1.0.0.0  ::
			................................
			:: - Copyright 2024           :: 
			:: - Erwin Graanstra          ::
			:: - krdy.online@gmail.com    ::
			................................
			"""
			);
    	
    }

}
