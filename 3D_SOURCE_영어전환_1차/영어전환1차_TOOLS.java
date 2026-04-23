import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.net.URI;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.stage.Modality;
import javafx.stage.StageStyle;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
	* CaptureManager - Screen Capture + IP 카메라 스트림 통합 클래스 (JavaFX 전용)
	*
	* ── Swing → JavaFX 변경사항 ──────────────────────────────────────────
	*   · JPanel / JFrame / ImageIcon 등 Swing 의존성 완전 제거
	*   · clockPanel: JPanel → javafx.scene.Node  (FX 스냅샷 캡처)
	*   · showImageWindow: JFrame → JavaFX Stage
	*   · Camera.FrameListener 콜백 타입: BufferedImage → WritableImage
	*     (WritableImage 는 FX 씬에 직접 주입 가능)
	*
	* ══════════════════════════════════════════════════════════════════
	*  ScreenCapture 기능 (CaptureManager 인스턴스 메서드)
	* ══════════════════════════════════════════════════════════════════
	*   captureClockScreen()  : ClockNode 스냅샷 → 임h PNG   ★ FX 스레드 필요
	*   captureFullScreen()   : All Monitor 캡처  → 임h PNG
	*   captureMonitor(int)   : 특정 Monitor 캡처  → 임h PNG
	*   showImageWindow(File) : 수신 Image를 JavaFX Stage 서브 윈도우에 표시
	*
	* ══════════════════════════════════════════════════════════════════
	*  Camera 기능 (CaptureManager.Camera 이너 클래스)
	* ══════════════════════════════════════════════════════════════════
	*   cam.start(url)          : MJPEG 스트림 수신 시작
	*   cam.stop()              : Stream stop
	*   cam.capture(dir)        : 현재 프레임을 dir/img/cam_*.jpg Save
	*   cam.isRunning()         : 스트림 실행 여부
	*   cam.getLastFrame()      : 마지막 수신 WritableImage  (씬 주입용)
	*   cam.getLastFrameAWT()   : 마지막 수신 BufferedImage (Save file용)
*/
public class TOOLS {
	
	public static boolean yesNoTimerConfirm(Stage theStage , String title, String labelMessage, String timerlMessage , int second , boolean _NO ) {	
		String green =  "#4CAF50;";
		String red =   "#F44336;";
        final boolean[] confirmed = {false};
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle(title);
        dlg.setAlwaysOnTop(true);
        Label msg = new Label(labelMessage);
        msg.setStyle("-fx-font-family:'Malgun Gothic';" + "-fx-text-fill:" + green + "-fx-font-size:13px;");
        Button yes = new Button("Yes");
        Button no  = new Button("No");
        yes.setPrefWidth(72); no.setPrefWidth(72);
        final int[]     sec       = {second};
        Label timerLbl = new Label(timerlMessage);
        timerLbl.setStyle("-fx-text-fill:" + green + " -fx-font-size:11px;");
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                timerLbl.setText("Auto cancel in: " + sec[0] + "s");
                dlg.setTitle("Confirm Exit — " + sec[0] + "s auto cancel");
                if (sec[0] <= 0) dlg.close();
			})
		);
        countdown.setCycleCount(second);
        countdown.play();
        yes.setOnAction(e -> { confirmed[0] = true;  countdown.stop(); dlg.close(); });
        no .setOnAction(e -> { confirmed[0] = false; countdown.stop(); dlg.close(); });
		
		HBox btns;
        if ( _NO ) { btns = new HBox(10, yes, no);}
		else {btns = new HBox(10, yes);}
		
        btns.setAlignment(Pos.CENTER);
        VBox root = new VBox(12, msg, timerLbl, btns);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER);
        dlg.setScene(new Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
		return confirmed[0];
	}
	
	public static class InstallNotepadPP {
		// ── 우선순위: ini Save값 → 시스템 영역 → Enable자 영역(portable ZIP) ──

		static final String INI_KEY = "npp.exePath";

		/** 시스템 영역 후보 Path */
		static final String[] SYSTEM_PATHS = {
			"C:\\Program Files\\Notepad++\\notepad++.exe",
			"C:\\Program Files (x86)\\Notepad++\\notepad++.exe"
		};

		/** Enable자 영역 설치 디렉터리: %LOCALAPPDATA%\Notepad++ */
		public static Path getUserInstallDir() {
			return Paths.get(System.getProperty("user.home"), "AppData", "Local", "Notepad++");
		}
		public static Path getUserExePath() {
			return getUserInstallDir().resolve("notepad++.exe");
		}

		/** ini에서 Save된 Path 읽기 */
		public static String getSavedPath() {
			return AppContext.get(INI_KEY, "");
		}

		/** Path를 ini에 Save */
		static void savePath(String path) {
			AppContext.set(INI_KEY, path);
			AppContext.save();
			System.out.println("[NppInstall] Path Save: " + path);
		}

		/**
		 * Enable 가능한 notepad++.exe Path 반환.
		 * 우선순위: (1) ini Save값 → (2) 시스템 영역 → (3) Enable자 영역
		 * 새로 발견 h ini에 자동 Save.
		 * @return Path 문자열, 미설치 h ""
		 */
		public static String getExePath() {
			// 1. ini Save값
			String saved = getSavedPath();
			if (!saved.isEmpty() && Files.exists(Paths.get(saved))) return saved;

			// 2. 시스템 영역 (Program Files)
			for (String p : SYSTEM_PATHS) {
				if (Files.exists(Paths.get(p))) {
					savePath(p);
					return p;
				}
			}

			// 3. Enable자 영역
			Path userExe = getUserExePath();
			if (Files.exists(userExe)) {
				savePath(userExe.toString());
				return userExe.toString();
			}

			return "";
		}

		/** 설치 여부 OK (시스템/Enable자 영역 모두) */
		public static boolean isAlreadyInstalled() {
			return !getExePath().isEmpty();
		}

		static boolean is64BitWindows() {
			String arch  = System.getProperty("os.arch", "").toLowerCase();
			String wow64 = System.getenv("PROCESSOR_ARCHITEW6432");
			String pa    = System.getenv("PROCESSOR_ARCHITECTURE");
			return arch.contains("64")
				|| (wow64 != null && wow64.contains("64"))
				|| (pa    != null && pa.contains("64"));
		}

		/** 동기 설치: Enable자 영역에 portable ZIP 배포 (백그라운드 스레드에서 호출) */
		public static void install() {
			try {
				if (isAlreadyInstalled()) {
					System.out.println("[NppInstall] 이미 설치됨: " + getExePath());
					return;
				}
				installPortableToUserArea();
			} catch (Exception e) { e.printStackTrace(); }
		}

		static void installPortableToUserArea() throws Exception {
			HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.connectTimeout(Duration.ofSeconds(20)).build();

			String html   = fetchLatestReleasePage(client);
			String zipUrl = extractPortableZipUrl(html, is64BitWindows());
			if (zipUrl == null)
				throw new RuntimeException(
					"Portable ZIP link not found.\nManual install: https://notepad-plus-plus.org/");

			System.out.println("[NppInstall] URL: " + zipUrl);
			Path tempDir = Files.createTempDirectory("npp_portable_");
			Path zipFile = tempDir.resolve(extractFileName(zipUrl));

			System.out.println("[NppInstall] Downloading...");
			downloadFile(client, zipUrl, zipFile);
			System.out.println("[NppInstall] Download Done " + Files.size(zipFile)/1024 + " KB");

			Path installDir = getUserInstallDir();
			Files.createDirectories(installDir);
			System.out.println("[NppInstall] 압축 해제: " + installDir);
			unzip(zipFile, installDir);

			try { Files.deleteIfExists(zipFile); Files.deleteIfExists(tempDir); }
			catch (Exception ignored) {}

			// 설치 후 Path 확인 및 ini Save
			if (Files.exists(getUserExePath())) {
				savePath(getUserExePath().toString());
				System.out.println("[NppInstall] Done: " + installDir);
			} else {
				System.out.println("[NppInstall] 설치 후 notepad++.exe 를 not found.");
			}
		}

		static String extractPortableZipUrl(String html, boolean is64) {
			String[] patterns = is64
				? new String[]{
					"href=\"([^\"]*?/download/[^\"/]+/npp\\.[^\"]*?\\.portable\\.x64\\.zip)\"",
					"href=\"([^\"]*?/download/[^\"/]+/npp\\.[^\"]*?\\.portable\\.zip)\"" }
				: new String[]{
					"href=\"([^\"]*?/download/[^\"/]+/npp\\.[^\"]*?\\.portable\\.zip)\"" };
			for (String regex : patterns) {
				Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(html);
				while (m.find()) {
					String p = m.group(1);
					if (!is64 && p.toLowerCase().contains(".x64.")) continue;
					return p.startsWith("http") ? p : "https://github.com" + p;
				}
			}
			return null;
		}

		static void unzip(Path zipFile, Path destDir) throws IOException {
			try (java.util.zip.ZipInputStream zis =
				new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
				java.util.zip.ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					Path out = destDir.resolve(entry.getName()).normalize();
					if (!out.startsWith(destDir)) { zis.closeEntry(); continue; }
					if (entry.isDirectory()) { Files.createDirectories(out); }
					else {
						Files.createDirectories(out.getParent());
						Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
					zis.closeEntry();
				}
			}
		}

		static String fetchLatestReleasePage(HttpClient client) throws Exception {
			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(
					"https://github.com/notepad-plus-plus/notepad-plus-plus/releases/latest"))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "Mozilla/5.0").GET().build();
			HttpResponse<String> res = client.send(req,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200)
				throw new IOException("Release page query failed: HTTP " + res.statusCode());
			return res.body();
		}
		static String extractFileName(String url) {
			return URLDecoder.decode(url.substring(url.lastIndexOf('/')+1), StandardCharsets.UTF_8);
		}
		static void downloadFile(HttpClient client, String url, Path target) throws Exception {
			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url)).timeout(Duration.ofMinutes(5))
				.header("User-Agent", "Mozilla/5.0").GET().build();
			HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(target));
			if (res.statusCode() != 200)
				throw new IOException("Download failed: HTTP " + res.statusCode());
		}

		// ── JavaFX Integrations ─────────────────────────────────────────────
		public static void installAsync(javafx.stage.Stage owner) {
			new Thread(() -> {
				try {
					String existing = getExePath();
					if (!existing.isEmpty()) {
						showResult(owner, "Notepad++ Install",
							"\u2705 Already installed.\n\nLocation: " + existing); return;
					}
					showResult(owner, "Notepad++ Install",
						"\u23f3 Downloading Portable version to user folder.\n\n"
						+ "Install location: " + getUserInstallDir() + "\n"
						+ "No admin rights needed \u00b7 Result shown when complete.");
					install();
					String installed = getExePath();
					if (!installed.isEmpty())
						showResult(owner, "Notepad++ Install",
							"\u2705 Installation complete!\n\nLocation: " + installed);
					else
						showResult(owner, "Notepad++ Install",
							"\u274c Installation failed\n\nManual install: https://notepad-plus-plus.org/downloads/");
				} catch (Exception e) {
					showResult(owner, "Notepad++ Install", "\u274c Error: " + e.getMessage());
				}
			}, "NppInstall").start();
		}
		private static void showResult(javafx.stage.Stage owner, String title, String msg) {
			javafx.application.Platform.runLater(() ->
				yesNoTimerConfirm(owner, title, msg, msg, 15, false));
		}
	}

		public static class InstallNotepadPP_Admin {
		public static void InstallNotepadPP_Admin(String[] args) {
			try {
				if (isAlreadyInstalled()) {
					System.out.println("Notepad++ Already installed.");
					return;
				}
				if (isWingetAvailable()) {
					System.out.println("winget 감지됨. winget으로 설치 시도...");
					int code = installWithWinget();
					System.out.println("winget 종료 코드: " + code);
					if (code == 0 && isAlreadyInstalled()) {
						System.out.println("Notepad++ Install Done (winget)");
						return;
					}
					System.out.println("winget 설치 Failed 또는 설치 확인 Failed. 직접 Download 방식으로 진행...");
					} else {
					System.out.println("winget 없음. 직접 Download 방식으로 진행...");
				}
				installByDirectDownload();
				} catch (Exception e) {
				e.printStackTrace();
			}
		}
		static boolean isAlreadyInstalled() {
			String[] paths = {
                "C:\\Program Files\\Notepad++\\notepad++.exe",
                "C:\\Program Files (x86)\\Notepad++\\notepad++.exe"
			};
			for (String p : paths) {
				if (Files.exists(Paths.get(p))) {
					return true;
				}
			}
			return false;
		}
		static boolean isWingetAvailable() {
			try {
				ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "winget --version");
				pb.redirectErrorStream(true);
				Process p = pb.start();
				drain(p.getInputStream());
				return p.waitFor() == 0;
				} catch (Exception e) {
				return false;
			}
		}
		static int installWithWinget() throws Exception {
			ProcessBuilder pb = new ProcessBuilder(
                "cmd", "/c",
                "winget install --id Notepad++.Notepad++ -e --silent --accept-package-agreements --accept-source-agreements"
			);
			pb.redirectErrorStream(true);
			
			Process p = pb.start();
			pipeToStdout(p.getInputStream());
			return p.waitFor();
		}
		static void installByDirectDownload() throws Exception {
			HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(20))
			.build();
			String html = fetchLatestReleasePage(client);
			boolean is64 = is64BitWindows();
			String installerUrl = extractInstallerUrl(html, is64);
			if (installerUrl == null) {
				throw new RuntimeException("Latest Notepad++ install link not found.");
			}
			System.out.println("OS 아키텍처: " + (is64 ? "64-bit" : "32-bit"));
			System.out.println("Installer URL: " + installerUrl);
			Path tempDir = Files.createTempDirectory("npp_install_");
			Path installer = tempDir.resolve(extractFileName(installerUrl));
			downloadFile(client, installerUrl, installer);
			System.out.println("Download Done: " + installer);
			int code = runInstaller(installer);
			System.out.println("설치 종료 코드: " + code);
			if (code == 0 && isAlreadyInstalled()) {
				System.out.println("Notepad++ Install Done (직접 Download)");
				} else if (code == 0) {
				System.out.println("설치 명령은 정상 종료했지만 설치 확인은 Failed했습니다.");
				} else {
				System.out.println("설치 Failed");
			}
		}
		static boolean is64BitWindows() {
			String arch = System.getProperty("os.arch", "").toLowerCase();
			String wow64 = System.getenv("PROCESSOR_ARCHITEW6432");
			String pa = System.getenv("PROCESSOR_ARCHITECTURE");
			
			return arch.contains("64")
			|| (wow64 != null && wow64.contains("64"))
			|| (pa != null && pa.contains("64"));
		}
		static String fetchLatestReleasePage(HttpClient client) throws Exception {
			HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("https://github.com/notepad-plus-plus/notepad-plus-plus/releases/latest"))
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", "Mozilla/5.0")
			.GET()
			.build();
			HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
			);
			if (response.statusCode() != 200) {
				throw new IOException("Release page query failed: HTTP " + response.statusCode());
			}
			return response.body();
		}
		static String extractInstallerUrl(String html, boolean is64) {
			String regex;
			if (is64) {
				regex = "href=\"([^\"]*?/download/[^\"/]+/npp\\.[^\"]*?Installer\\.x64\\.exe)\"";
				} else {
				regex = "href=\"([^\"]*?/download/[^\"/]+/npp\\.[^\"]*?Installer\\.exe)\"";
			}
			Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(html);
			while (matcher.find()) {
				String path = matcher.group(1);
				if (!is64 && path.toLowerCase().contains(".x64.")) {
					continue;
				}
				if (path.startsWith("http://") || path.startsWith("https://")) {
					return path;
				}
				return "https://github.com" + path;
			}
			return null;
		}
		static String extractFileName(String url) {
			String raw = url.substring(url.lastIndexOf('/') + 1);
			return URLDecoder.decode(raw, StandardCharsets.UTF_8);
		}
		static void downloadFile(HttpClient client, String url, Path target) throws Exception {
			HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofMinutes(3))
			.header("User-Agent", "Mozilla/5.0")
			.GET()
			.build();
			HttpResponse<Path> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofFile(target)
			);
			if (response.statusCode() != 200) {
				throw new IOException("Download failed: HTTP " + response.statusCode());
			}
		}
		static int runInstaller(Path installer) throws Exception {
			ProcessBuilder pb = new ProcessBuilder(
                installer.toAbsolutePath().toString(),
                "/S"
			);
			pb.directory(installer.getParent().toFile());
			pb.redirectErrorStream(true);
			
			Process p = pb.start();
			pipeToStdout(p.getInputStream());
			return p.waitFor();
		}
		static void pipeToStdout(InputStream in) throws IOException {
			try (BufferedReader br = new BufferedReader(
			new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
			}
			}
		}
		static void drain(InputStream in) throws IOException {
			byte[] buffer = new byte[1024];
			while (in.read(buffer) != -1) {
			}
		}
		/**
			* JavaFX 메뉴에서 호출 — 백그라운드 스레드로 설치 후 결과 Alert 표시.
			* @param owner Alert의 부모 Stage (null 가능)
		*/
		public static void installAsync(javafx.stage.Stage owner) {
			new Thread(() -> {
				try {
					if (isAlreadyInstalled()) {
						showResult(owner, "Notepad++ Install",
						"✅ Notepad++ is already installed.");
						return;
					}
					showResult(owner, "Notepad++ Install",
						"⏳ Starting Notepad++ installation.\n\n"
						+ "Installing via winget or direct download.\n"
					+ "When done, result window will appear.");
					InstallNotepadPP_Admin(null);
					if (isAlreadyInstalled())
					showResult(owner, "Notepad++ Install", "✅ Notepad++ installation complete.");
					else
					showResult(owner, "Notepad++ Install",
						"❌ Installation failed.\n"
						+ "Try running as administrator or install manually.\n"
					+ "https://notepad-plus-plus.org/downloads/");
					} catch (Exception e) {
					showResult(owner, "Notepad++ Install", "❌ Install error: " + e.getMessage());
				}
			}, "NppInstall").start();
		}
		private static void showResult(javafx.stage.Stage owner, String title, String labelMessage) {
			javafx.application.Platform.runLater(() -> {
				/*
					javafx.scene.control.Alert a =
					new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
					if (owner != null) a.initOwner(owner);
					a.setTitle(title);
					a.setHeaderText(null);
					a.setContentText(labelMessage);
					a.show();
				*/
				yesNoTimerConfirm(owner, title, labelMessage, labelMessage, 15, false);
			});
		}
	}
	public static class CaptureManager {
		// ── 시계 노드 참조 (FX 스냅샷 캡처용) ──────────────────────────
		/** null 허용 — 캡처 불필요 h null 전달 가능 */
		private final javafx.scene.Node clockNode;
		/** 여러 Images 창이 겹치지 않도록 오프셋 순환 */
		private int imageWindowOffset = 0;
		// ── 생성자 ───────────────────────────────────────────────────────
		/**
			* @param clockNode 시계 씬 노드 (captureClockScreen 용). null 가능.
		*/
		public CaptureManager(javafx.scene.Node clockNode) {
			this.clockNode = clockNode;
		}
		// ═══════════════════════════════════════════════════════════════
		//  ScreenCapture 기능 — Screen Capture 및 Image 표시
		// ═══════════════════════════════════════════════════════════════
		/**
			* ClockNode 스냅샷을 캡처Other여 임h PNG File로 Save.
			* <b>반드h JavaFX Application Thread 에서 호출해야 한다.</b>
			* 백그라운드 스레드에서 필요Other면 Platform.runLater 로 래핑Other라.
			*
			* @return Save된 PNG File
		*/
		public File captureClockScreen() throws Exception {
			if (clockNode == null)
			throw new IllegalStateException("clockNode is not initialized.");
			WritableImage snapshot = clockNode.snapshot(null, null);
			BufferedImage  awtImg  = SwingFXUtils.fromFXImage(snapshot, null);
			File outFile = new File(System.getProperty("java.io.tmpdir"),
			"clock_capture_" + System.currentTimeMillis() + ".png");
			ImageIO.write(awtImg, "PNG", outFile);
			return outFile;
		}
		/**
			* 모든 Monitor를 포함한 Full screen을 캡처.
			* AWT Robot 을 EnableOther므로 백그라운드 스레드에서도 호출 가능.
		*/
		public File captureFullScreen() throws Exception {
			java.awt.Rectangle fullBounds = new java.awt.Rectangle();
			for (java.awt.GraphicsDevice gd :
				java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
				fullBounds = fullBounds.union(gd.getDefaultConfiguration().getBounds());
			}
			BufferedImage img = new java.awt.Robot().createScreenCapture(fullBounds);
			File outFile = new File(System.getProperty("java.io.tmpdir"),
			"screenshot_" + System.currentTimeMillis() + ".png");
			ImageIO.write(img, "PNG", outFile);
			return outFile;
		}
		
		/**
			* 특정 Monitor를 캡처.
			* @param monitorIndex 0 부터 시작Other는 Monitor 인덱스
		*/
		public File captureMonitor(int monitorIndex) throws Exception {
			java.awt.GraphicsDevice[] screens =
			java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
			if (monitorIndex >= screens.length)
			throw new Exception("Monitor " + (monitorIndex + 1) + " not found. "
			+ "(Connected monitors: " + screens.length + ")");
			java.awt.Rectangle bounds = screens[monitorIndex].getDefaultConfiguration().getBounds();
			BufferedImage img = new java.awt.Robot().createScreenCapture(bounds);
			File outFile = new File(System.getProperty("java.io.tmpdir"),
			"monitor" + (monitorIndex + 1) + "_" + System.currentTimeMillis() + ".png");
			ImageIO.write(img, "PNG", outFile);
			return outFile;
		}
		
		/**
			* Image File을 새 JavaFX Stage 서브 윈도우에 표시.
			* 화면 크기의 80% 를 최대 크기로 자동 스케.
			* 여러 창이 열릴 경우 30px 씩 오프셋Other여 겹침 방지.
			* 내부에서 Platform.runLater 를 EnableOther므로 어느 스레드에서나 호출 가능.
			*
			* @param imageFile 표시할 Image File
		*/
		public void showImageWindow(File imageFile) {
			final int offset = imageWindowOffset;
			imageWindowOffset = (imageWindowOffset + 1) % 10;
			
			Platform.runLater(() -> {
				try {
					Image fxImg = new Image(imageFile.toURI().toString(), true);
					
					javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
					double maxW = screen.getWidth()  * 0.80;
					double maxH = screen.getHeight() * 0.80;
					
					ImageView iv = new ImageView(fxImg);
					iv.setPreserveRatio(true);
					iv.setFitWidth(maxW);
					iv.setFitHeight(maxH);
					iv.setSmooth(true);
					
					StackPane pane = new StackPane(iv);
					pane.setPadding(new Insets(4));
					pane.setStyle("-fx-background-color: #1a1a1a;");
					
					Stage stage = new Stage(StageStyle.DECORATED);
					stage.setTitle("📷 " + imageFile.getName());
					stage.setAlwaysOnTop(true);
					stage.setScene(new Scene(pane, maxW + 8, maxH + 8, Color.BLACK));
					
					double ox = offset * 30;
					double oy = offset * 30;
					stage.setX(screen.getMinX() + (screen.getWidth()  - stage.getWidth())  / 2 + ox);
					stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2 + oy);
					
					stage.show();
					System.out.println("[ImageWindow] 표시: " + imageFile.getName());
					
					} catch (Exception e) {
					System.out.println("[ImageWindow] 표h Failed: " + e.getMessage());
				}
			});
		}
		
		// ═══════════════════════════════════════════════════════════════
		//  Camera — IP Webcam MJPEG 스트림 수신
		//
		//  IP Webcam MJPEG 포맷:
		//    Content-Type: multipart/x-mixed-replace; boundary=--myboundary
		//    각 File트: --myboundary\r\nContent-Type: image/jpeg\r\n\r\n<JPEG>\r\n
		//
		//  Save File명: img/cam_yyyyMMdd_HHmmss_SSS.jpg
		//
		//  Enable법:
		//    CaptureManager.Camera cam = new CaptureManager.Camera(frameListener);
		//    cam.start("http://192.168.x.x:8080");
		//    cam.stop();
		//    cam.capture(saveDir);
		// ═══════════════════════════════════════════════════════════════
		
		public static class Camera {
			
			/**
				* 새 프레임 도착 h 콜백.
				* WritableImage 는 JavaFX Image이므로 FX 씬에 즉h Apply 가능.
				* <b>콜백은 백그라운드(Camera-Reader) 스레드에서 호출된다.</b>
				* FX 씬 노드를 직접 수정Other려면 Platform.runLater 를 EnableOther라.
			*/
			public interface FrameListener {
				void onFrame(WritableImage frame);
			}
			
			private final FrameListener    listener;
			private volatile boolean       running      = false;
			private volatile BufferedImage lastFrameAWT = null;   // Save file용
			private volatile WritableImage lastFrame    = null;   // FX 씬 주입용
			private Thread readerThread;
			
			public Camera(FrameListener listener) {
				this.listener = listener;
			}
			
			public boolean isRunning()             { return running; }
			public boolean isConnected()           { return running && lastFrame != null; }
			/** 마지막 Wed신 JavaFX Images (FxGPUNeon 배경 주입용) */
			public WritableImage getLastFrame()    { return lastFrame; }
			/** 마지막 Wed신 AWT Images (Save file용) */
			public BufferedImage getLastFrameAWT() { return lastFrameAWT; }
			
			/** MJPEG 스트림 Wed신 시작 */
			public void start(String streamUrl) {
				stop();
				running = true;
				readerThread = new Thread(() -> {
					int failCount = 0;
					final int MAX_FAIL = 5;
					while (running) {
						try {
							connectAndRead(streamUrl);
							failCount = 0;
							} catch (Exception e) {
							if (running) {
								failCount++;
								System.out.println("[Camera] 연결 오류 (" + failCount + "/" + MAX_FAIL
								+ "), retrying in 3s: " + e.getMessage());
								if (failCount >= MAX_FAIL) {
									System.out.println("[Camera] 연속 " + MAX_FAIL + "회 Failed → 자동 중지");
									running      = false;
									lastFrame    = null;
									lastFrameAWT = null;
									break;
								}
								try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
							}
						}
					}
				}, "Camera-Reader");
				readerThread.setDaemon(true);
				readerThread.start();
				System.out.println("[Camera] Stream start: " + streamUrl);
			}
			
			/** 스트림 Stop */
			public void stop() {
				running = false;
				if (readerThread != null) {
					readerThread.interrupt();
					readerThread = null;
				}
				lastFrame    = null;
				lastFrameAWT = null;
				System.out.println("[Camera] Stream stop");
			}
			
			/**
				* 현재 프레임을 saveDir/img/ Folder에 Save.
				* File명: cam_yyyyMMdd_HHmmss_SSS.jpg
				* @return Save된 File Path (Failed h null)
			*/
			public String capture(File saveDir) {
				BufferedImage frame = lastFrameAWT;
				if (frame == null) {
					System.out.println("[Camera] Capture failed: 수신된 프레임 없음");
					return null;
				}
				try {
					File imgDir = new File(saveDir, "img");
					if (!imgDir.exists()) imgDir.mkdirs();
					
					String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
					File   file = new File(imgDir, "cam_" + ts + ".jpg");
					ImageIO.write(frame, "jpg", file);
					System.out.println("[Camera] Save Done: " + file.getAbsolutePath());
					return file.getAbsolutePath();
					} catch (Exception e) {
					System.out.println("[Camera] Save 오류: " + e.getMessage());
					return null;
				}
			}
			
			// ── MJPEG 스트림 File싱 ───────────────────────────────────────
			
			private void connectAndRead(String streamUrl) throws Exception {
				//  @SuppressWarnings("deprecation")
				// URL url = new URL(streamUrl + "/video")	;
				URL url = URI.create(streamUrl + "/video").toURL();
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(8000);
				conn.setReadTimeout(10000);
				conn.connect();
				
				String contentType = conn.getContentType();
				String boundary = "--myboundary";
				if (contentType != null && contentType.contains("boundary=")) {
					boundary = contentType.split("boundary=")[1].trim();
					if (!boundary.startsWith("--")) boundary = "--" + boundary;
				}
				
				InputStream in = new BufferedInputStream(conn.getInputStream(), 65536);
				
				while (running) {
					if (!skipToBoundary(in, boundary)) break;
					
					int contentLength = -1;
					String hLine;
					while (!(hLine = readLine(in)).isEmpty()) {
						if (hLine.toLowerCase().startsWith("content-length:")) {
							try { contentLength = Integer.parseInt(hLine.split(":")[1].trim()); }
							catch (Exception ignored) {}
						}
					}
					
					byte[] jpegBytes;
					if (contentLength > 0) {
						jpegBytes = readBytes(in, contentLength);
						} else {
						jpegBytes = readUntilBoundary(in, boundary);
					}
					if (jpegBytes == null || jpegBytes.length == 0) continue;
					
					try {
						BufferedImage awtImg = ImageIO.read(new ByteArrayInputStream(jpegBytes));
						if (awtImg != null) {
							// AWT → JavaFX WritableImage 변환 (백그라운드 스레드에서 안전)
							WritableImage fxImg = SwingFXUtils.toFXImage(awtImg, null);
							lastFrameAWT = awtImg;
							lastFrame    = fxImg;
							if (listener != null) listener.onFrame(fxImg);
						}
					} catch (Exception ignored) {}
				}
				conn.disconnect();
			}
			
			private boolean skipToBoundary(InputStream in, String boundary) throws IOException {
				while (running) {
					String line = readLine(in);
					if (line == null) return false;
					if (line.startsWith(boundary)) return true;
				}
				return false;
			}
			
			private String readLine(InputStream in) throws IOException {
				StringBuilder sb = new StringBuilder();
				int c;
				while ((c = in.read()) != -1) {
					if (c == '\n') break;
					if (c != '\r') sb.append((char) c);
				}
				return c == -1 ? null : sb.toString();
			}
			
			private byte[] readBytes(InputStream in, int len) throws IOException {
				byte[] buf = new byte[len];
				int    off = 0;
				while (off < len) {
					int n = in.read(buf, off, len - off);
					if (n < 0) break;
					off += n;
				}
				return buf;
			}
			
			private byte[] readUntilBoundary(InputStream in, String boundary) throws IOException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
				byte[] bnd = ("\r\n" + boundary).getBytes("UTF-8");
				int    idx = 0;
				int    c;
				while ((c = in.read()) != -1) {
					if (c == bnd[idx]) {
						idx++;
						if (idx == bnd.length) {
							byte[] data = baos.toByteArray();
							int end = data.length;
							if (end >= 2 && data[end-2] == '\r' && data[end-1] == '\n') end -= 2;
							return java.util.Arrays.copyOf(data, end);
						}
						} else {
						if (idx > 0) { baos.write(bnd, 0, idx); idx = 0; }
						baos.write(c);
					}
				}
				return baos.toByteArray();
			}
		}
	}
}
