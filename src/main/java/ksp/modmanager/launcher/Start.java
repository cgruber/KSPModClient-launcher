package ksp.modmanager.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.swing.JOptionPane;

/**
 * Hello world!
 * 
 */
public class Start {
	private static File home = new File(System.getProperty("user.home")
			+ "/.ksp-mm");

	public static void main(String[] args) throws Exception {
		try {
			home.mkdir();
			final File jar = new File(home, "KSPModClient.jar");
			downloadJar(jar, new File(home, "KSPModClient.meta"));
			startJar(jar, args);
		} catch (Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			JOptionPane.showMessageDialog(null, sw.toString());
			throw ex;
		}
	}

	private static void startJar(File jar, final String[] args)
			throws IOException, ClassNotFoundException, NoSuchMethodException,
			SecurityException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		JarFile jarfile = new JarFile(jar);
		Manifest manifest = jarfile.getManifest();
		String main = manifest.getMainAttributes().getValue("Main-Class");
		jarfile.close();

		URLClassLoader child = new URLClassLoader(new URL[] { jar.toURI()
				.toURL() }, Start.class.getClassLoader());
		Class<?> mainClass = Class.forName(main, true, child);

		Method m = mainClass.getMethod("main", String[].class);

		m.invoke(null, (Object) args);
	}

	private static void downloadJar(File jar, File metadata) throws IOException {
		System.out.println("Checking for updates...");

		String etag = null;
		String lastModified = null;

		if (jar.exists()) {
			try (BufferedReader read = new BufferedReader(
					new InputStreamReader(new FileInputStream(metadata)))) {
				lastModified = read.readLine();
				etag = read.readLine();
			} catch (IOException ex) {
			}
		}

		FileDownload download = downloadFile("KSPModManager-latest.jar", etag,
				lastModified);
		if (download != null) {
			System.out.println("Update detected. Downloading...");
			Files.copy(download.inputStream, Paths.get(jar.toURI()),
					StandardCopyOption.REPLACE_EXISTING);
			download.inputStream.close();

			try (PrintWriter writer = new PrintWriter(metadata)) {
				writer.println(download.lastModifiedSince);
				writer.println(download.etag);
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			System.out.println("Updated to " + download.lastModifiedSince);
		} else {
			System.out.println("No update necessary. Running version "
					+ lastModified);
		}
	}

	private static FileDownload downloadFile(String filename, String etag,
			String lastModified) throws IOException {
		File metadata = new File(filename + ".meta");

		URL sourceURL = new URL("http://ovh.minichan.org/ksp/" + filename);
		HttpURLConnection.setFollowRedirects(true);
		HttpURLConnection sourceConnection = (HttpURLConnection) sourceURL
				.openConnection();
		sourceConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");

		if (etag != null) {
			sourceConnection.addRequestProperty("If-None-Match", etag);
		}

		if (lastModified != null) {
			sourceConnection.addRequestProperty("If-Modified-Since",
					lastModified);
		}

		sourceConnection.connect();

		String encoding = sourceConnection.getContentEncoding();

		if (sourceConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
			sourceConnection.disconnect();
			return null;
		}

		InputStream resultingInputStream = null;

		// create the appropriate stream wrapper based on
		// the encoding type
		if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
			resultingInputStream = new GZIPInputStream(
					sourceConnection.getInputStream());
		} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			resultingInputStream = new InflaterInputStream(
					sourceConnection.getInputStream(), new Inflater(true));
		} else {
			resultingInputStream = sourceConnection.getInputStream();
		}

		return new FileDownload(resultingInputStream,
				sourceConnection.getHeaderField("ETag"),
				sourceConnection.getHeaderField("Last-Modified"));
	}

	private static class FileDownload {
		public final InputStream inputStream;
		public final String etag, lastModifiedSince;

		public FileDownload(InputStream inputStream, String etag,
				String lastModifiedSince) {
			this.inputStream = inputStream;
			this.etag = etag;
			this.lastModifiedSince = lastModifiedSince;
		}

	}
}
