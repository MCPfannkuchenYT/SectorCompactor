package sectorcompactor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Compactor {
	
	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException {
		boolean hasPrepared = false;
		int task = -1;
		int sectorSize = -1;
		File origFile = null;
		File inFile = null;
		if (args.length >= 4) {
			if (Character.isDigit(args[2].charAt(0))) {
				hasPrepared = true;
				task = Integer.parseInt(args[2]);
				origFile = new File(args[0].replaceAll("\"", "").replace("\\", "/"));
				inFile = new File(args[1].replaceAll("\"", "").replace("\\", "/"));
				sectorSize = Integer.parseInt(args[3]);
			}
		}
		
		Scanner sc = new Scanner(System.in);
		System.out.println("===============================");
		System.out.println("|         VMDKompress         |");
		System.out.println("|            v1.0.0           |");
		System.out.println("===============================");
		System.out.println();
		System.out.println("Select the original file:");
		if (!hasPrepared) origFile = new File(sc.nextLine().replaceAll("\"", "").replace("\\", "/"));
		else System.out.println(args[0]);
		System.out.println("Select the input file:");
		if (!hasPrepared) inFile = new File(sc.nextLine().replaceAll("\"", "").replace("\\", "/"));
		else System.out.println(args[1]);
		System.out.println("Do you want to compress (1) or decompress (2)?");
		if (!hasPrepared) task = sc.nextInt();
		else System.out.println(args[2]);
		System.out.println("Select the sector size:");
		if (!hasPrepared) {
			sectorSize = sc.nextInt();
			sc.nextLine();
		}
		else System.out.println(args[3]);
		if (task == 1) {
			File patchFile = new File(inFile.getName() + ".patch");
			File gzFile = new File(patchFile.getName() + ".gz");
			long outLength = inFile.length();
			int sectors = (int) Math.ceil(outLength / sectorSize);
			FileChannel inStream = new FileInputStream(inFile).getChannel();
			FileChannel origStream = new FileInputStream(origFile).getChannel();
			FileChannel patchStream = new FileOutputStream(patchFile).getChannel();
			ByteBuffer inBuf = ByteBuffer.allocateDirect(sectorSize);
			ByteBuffer origBuf = ByteBuffer.allocateDirect(sectorSize);
			patchStream.write(ByteBuffer.allocate(4).putInt(sectors).flip());
			for (long sector = 0; sector < sectors; sector++) {
				inStream.read(inBuf);
				origStream.read(origBuf);
				inBuf.flip();
				origBuf.flip();
				if (inBuf.compareTo(origBuf) != 0) patchStream.write(ByteBuffer.allocate(8 + sectorSize).putLong(sector).put(inBuf).flip());
				inBuf.clear();
				origBuf.clear();
				System.out.print(sector+1 + "/" + sectors + " sectors read\r");
			}
			System.out.println();
			inStream.close();
			origStream.close();
			patchStream.close();
			GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(gzFile, false));
			System.out.println("Compresing data...");
			Files.copy(patchFile.toPath(), gz);
			System.out.println("Leaving with " + gzFile.length() / 1024 / 1024.0 + "MB.");
			gz.close();
		} else if (task == 2) {
			System.out.println("Select the Compressed Patch File:");
			File gzFile = null;
			if (!hasPrepared) gzFile = new File(sc.nextLine().replaceAll("\"", "").replace("\\", "/"));
			else gzFile = new File(args[4].replaceAll("\"", "").replace("\\", "/"));
			GZIPInputStream in = new GZIPInputStream(new FileInputStream(gzFile));
			System.out.println("Decompressing data...");
			File patchFile = new File(inFile.getName() + ".patch");
			Files.copy(in, patchFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			inFile.createNewFile();
			FileChannel outStream = new FileOutputStream(inFile).getChannel();
			FileChannel origStream = new FileInputStream(origFile).getChannel();
			FileChannel patchStream = new FileInputStream(patchFile).getChannel();
			ByteBuffer bf = ByteBuffer.allocateDirect(4);
			patchStream.read(bf);
			bf.flip();
			int sectors = bf.getInt();
			
			ByteBuffer patchPosBuf = ByteBuffer.allocateDirect(8); 
			patchStream.read(patchPosBuf);
			patchPosBuf.flip();
			ByteBuffer patchBuf = ByteBuffer.allocateDirect(sectorSize); 
			patchStream.read(patchBuf);
			patchBuf.flip();
			ByteBuffer origBuf = ByteBuffer.allocateDirect(sectorSize);
			long sectorToStopAt = patchPosBuf.getLong();
			for (long sector = 0; sector < sectors; sector++) {
				origStream.read(origBuf);
				origBuf.flip();
				if (sector == sectorToStopAt) {
					outStream.write(patchBuf);
					patchBuf.clear();
					patchPosBuf.clear();
					if (patchStream.size() == patchStream.position()) continue;
					patchStream.read(patchPosBuf);
					patchPosBuf.flip();
					sectorToStopAt = patchPosBuf.getLong();
					patchStream.read(patchBuf);
					patchBuf.flip();
				} else {
					outStream.write(origBuf);
				}
				origBuf.clear();
				System.out.print(sector+1 + "/" + sectors + " sectors read\r");
			}
			outStream.close();
		}
	}
	
}
