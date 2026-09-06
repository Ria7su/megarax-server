package megarax.accountsync;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulls pending web registrations over HTTPS and inserts them into the
 * LoginServer database using prepared statements. Passwords arrive already
 * converted to the exact Base64(SHA-1) representation expected by L2J Mobius.
 */
public final class MegaraxAccountSync
{
	private static final String INSERT_ACCOUNT =
		"INSERT INTO accounts (login, password, email, lastactive, accessLevel, lastServer) VALUES (?, ?, ?, 0, 0, 1)";
	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(HTTP_TIMEOUT)
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private final URI pendingUri;
	private final URI completeUri;
	private final URI heartbeatUri;
	private final String secret;
	private final String jdbcUrl;
	private final String jdbcUser;
	private final String jdbcPassword;
	private final long startedAt = System.currentTimeMillis();

	private MegaraxAccountSync(Path projectRoot, Path syncConfig) throws IOException
	{
		final Map<String, String> sync = readIni(syncConfig);
		final Map<String, String> database = readIni(projectRoot.resolve("login/config/Database.ini"));
		final String siteUrl = required(sync, "SiteUrl").replaceAll("/+$", "");
		final URI siteUri = URI.create(siteUrl);
		final boolean secure = "https".equalsIgnoreCase(siteUri.getScheme());
		final boolean localTest =
			"http".equalsIgnoreCase(siteUri.getScheme()) &&
			("localhost".equalsIgnoreCase(siteUri.getHost()) || "127.0.0.1".equals(siteUri.getHost()));
		if (!secure && !localTest)
		{
			throw new IllegalArgumentException("SiteUrl must use HTTPS except for a local loopback test.");
		}
		pendingUri = URI.create(siteUrl + "/api/account-sync/pending");
		completeUri = URI.create(siteUrl + "/api/account-sync/complete");
		heartbeatUri = URI.create(siteUrl + "/api/server-status/heartbeat");
		secret = required(sync, "SyncSecret");
		if (secret.length() < 32)
		{
			throw new IllegalArgumentException("SyncSecret must contain at least 32 characters.");
		}
		jdbcUrl = required(database, "URL");
		jdbcUser = required(database, "Login");
		jdbcPassword = database.getOrDefault("Password", "");
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			System.err.println("Usage: MegaraxAccountSync <project-root> <account-sync.ini>");
			System.exit(2);
		}
		final Path projectRoot = Path.of(args[0]).toAbsolutePath().normalize();
		final Path syncConfig = Path.of(args[1]).toAbsolutePath().normalize();
		new MegaraxAccountSync(projectRoot, syncConfig).run();
	}

	private void run() throws InterruptedException
	{
		log("Account registration synchronizer started.");
		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				sendHeartbeat();
				for (Registration registration : fetchPending())
				{
					process(registration);
				}
			}
			catch (Exception e)
			{
				log("Synchronization cycle failed: " + safeMessage(e));
			}
			Thread.sleep(POLL_INTERVAL);
		}
	}

	private void sendHeartbeat() throws IOException, InterruptedException
	{
		int playersOnline = 0;
		try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
			PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE online = 1");
			ResultSet result = statement.executeQuery())
		{
			if (result.next())
			{
				playersOnline = result.getInt(1);
			}
		}
		catch (SQLException e)
		{
			log("Could not read online player count: " + safeMessage(e));
		}
		final boolean loginOnline = isPortOpen(2106);
		final boolean gameOnline = isPortOpen(7777);
		final String body = "{\"loginOnline\":" + loginOnline +
			",\"gameOnline\":" + gameOnline +
			",\"playersOnline\":" + playersOnline +
			",\"startedAt\":" + startedAt + "}";
		final HttpRequest request = HttpRequest.newBuilder(heartbeatUri)
			.timeout(HTTP_TIMEOUT)
			.header("Authorization", "Bearer " + secret)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();
		final HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
		if (response.statusCode() != 200)
		{
			throw new IOException("Heartbeat endpoint returned HTTP " + response.statusCode());
		}
	}

	private static boolean isPortOpen(int port)
	{
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
			return true;
		}
		catch (IOException e)
		{
			return false;
		}
	}

	private List<Registration> fetchPending() throws IOException, InterruptedException
	{
		final HttpRequest request = HttpRequest.newBuilder(pendingUri)
			.timeout(HTTP_TIMEOUT)
			.header("Authorization", "Bearer " + secret)
			.header("Accept", "text/plain")
			.GET()
			.build();
		final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200)
		{
			throw new IOException("Pending endpoint returned HTTP " + response.statusCode());
		}
		final List<Registration> registrations = new ArrayList<>();
		for (String line : response.body().split("\\R"))
		{
			if (line.isBlank())
			{
				continue;
			}
			final String[] fields = line.split("\\t", -1);
			if ((fields.length != 4) || !fields[0].matches("[0-9a-fA-F-]{36}"))
			{
				throw new IOException("Pending endpoint returned a malformed record.");
			}
			registrations.add(new Registration(fields[0], decode(fields[1]), decode(fields[2]), decode(fields[3])));
		}
		return registrations;
	}

	private void process(Registration registration)
	{
		String status = "REJECTED";
		String message = "No se pudo crear la cuenta.";
		try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
			PreparedStatement statement = connection.prepareStatement(INSERT_ACCOUNT))
		{
			statement.setString(1, registration.login());
			statement.setString(2, registration.passwordHash());
			statement.setString(3, registration.email());
			statement.executeUpdate();
			status = "SYNCED";
			message = "Tu cuenta fue creada correctamente.";
			log("Created account " + registration.login() + ".");
		}
		catch (SQLException e)
		{
			if ("23000".equals(e.getSQLState()))
			{
				message = "Ese nombre de cuenta ya existe en el servidor.";
				log("Rejected duplicate account " + registration.login() + ".");
			}
			else
			{
				message = "El servidor no pudo guardar la cuenta. Inténtalo más tarde.";
				log("Database error while creating " + registration.login() + ": " + safeMessage(e));
			}
		}
		try
		{
			complete(registration.id(), status, message);
		}
		catch (Exception e)
		{
			log("Could not acknowledge registration " + registration.id() + ": " + safeMessage(e));
		}
	}

	private void complete(String id, String status, String message) throws IOException, InterruptedException
	{
		final String body = "id=" + encode(id) + "&status=" + encode(status) + "&message=" + encode(message);
		final HttpRequest request = HttpRequest.newBuilder(completeUri)
			.timeout(HTTP_TIMEOUT)
			.header("Authorization", "Bearer " + secret)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
			.build();
		final HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
		if (response.statusCode() != 200)
		{
			throw new IOException("Completion endpoint returned HTTP " + response.statusCode());
		}
	}

	private static Map<String, String> readIni(Path path) throws IOException
	{
		final Map<String, String> values = new HashMap<>();
		for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8))
		{
			final String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";"))
			{
				continue;
			}
			final int separator = line.indexOf('=');
			if (separator > 0)
			{
				values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
			}
		}
		return values;
	}

	private static String required(Map<String, String> values, String key)
	{
		final String value = values.get(key);
		if ((value == null) || value.isBlank())
		{
			throw new IllegalArgumentException("Missing required setting: " + key);
		}
		return value;
	}

	private static String decode(String value)
	{
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	private static String encode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String safeMessage(Exception e)
	{
		final String message = e.getMessage();
		return (message == null) ? e.getClass().getSimpleName() : message.replaceAll("[\\r\\n]+", " ");
	}

	private static void log(String message)
	{
		System.out.println(Instant.now() + " " + message);
	}

	private record Registration(String id, String login, String passwordHash, String email)
	{
	}
}
