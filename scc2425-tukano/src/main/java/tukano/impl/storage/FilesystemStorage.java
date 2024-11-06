package main.java.tukano.impl.storage;


import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.ok;
import static main.java.tukano.api.Result.ErrorCode.BAD_REQUEST;
import static main.java.tukano.api.Result.ErrorCode.CONFLICT;
import static main.java.tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static main.java.tukano.api.Result.ErrorCode.NOT_FOUND;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

import main.java.tukano.api.Result;
import main.java.utils.Hash;
import main.java.utils.IO;

/**
 * FilesystemStorage is an implementation of the BlobStorage interface for storing
 * files in the local file system.
 */
public class FilesystemStorage implements BlobStorage {
	private final String rootDir;
	private static final int CHUNK_SIZE = 4096;
	private static final String DEFAULT_ROOT_DIR = "/tmp/";

	public FilesystemStorage() {
		this.rootDir = DEFAULT_ROOT_DIR;
	}

	/**
	 * Writes a blob (byte array) to the filesystem.
	 *
	 * @param path The path where the file will be stored
	 * @param bytes The byte array representing the content to be written
	 * @return A Result indicating success or failure of the write operation
	 */
	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);

		var file = toFile( path );

		if (file.exists()) {
			if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(IO.read(file))))
				return ok();
			else
				return error(CONFLICT);

		}
		IO.write(file, bytes);
		return ok();
	}

	/**
	 * Reads a blob from the filesystem.
	 *
	 * @param path The path to the file to be read
	 * @return A Result containing the byte array or an error if the file does not exist
	 */
	@Override
	public Result<byte[]> read(String path) {
		if (path == null)
			return error(BAD_REQUEST);
		
		var file = toFile( path );
		if( ! file.exists() )
			return error(NOT_FOUND);
		
		var bytes = IO.read(file);
		return bytes != null ? ok( bytes ) : error( INTERNAL_ERROR );
	}

	/**
	 * Reads a blob from the filesystem in chunks.
	 * It uses a consumer to process each chunk of the file.
	 *
	 * @param path The path to the file to be read
	 * @param sink A consumer that processes each chunk of the file
	 * @return A Result indicating success or failure of the read operation
	 */
	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);
		
		var file = toFile( path );
		if( ! file.exists() )
			return error(NOT_FOUND);
		
		IO.read( file, CHUNK_SIZE, sink );
		return ok();
	}

	/**
	 * Deletes a blob (file) from the filesystem.
	 *
	 * @param path The path to the file to be deleted
	 * @return A Result indicating success or failure of the delete operation
	 */
	@Override
	public Result<Void> delete(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		try {
			var file = toFile( path );
			Files.walk(file.toPath())
			.sorted(Comparator.reverseOrder())
			.map(Path::toFile)
			.forEach(File::delete);
		} catch (IOException e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
		return ok();
	}

	/**
	 * Converts a path string to a File object.
	 * It ensures that the parent directories of the file exist, creating them if necessary.
	 *
	 * @param path The path to the file
	 * @return The corresponding File object
	 */
	private File toFile(String path) {
		var res = new File( rootDir + path );
		
		var parent = res.getParentFile();
		if( ! parent.exists() )
			parent.mkdirs();
		
		return res;
	}

	
}
