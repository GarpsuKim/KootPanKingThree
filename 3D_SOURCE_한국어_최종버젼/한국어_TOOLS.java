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
	* CaptureManager - 화면 캡처 + IP 카메라 스트림 통합 클래스 (JavaFX 전용)
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
	*   captureClockScreen()  : ClockNode 스냅샷 → 임시 PNG   ★ FX 스레드 필요
	*   captureFullScreen()   : 전체 모니터 캡처  → 임시 PNG
	*   captureMonitor(int)   : 특정 모니터 캡처  → 임시 PNG
	*   showImageWindow(File) : 수신 이미지를 JavaFX Stage 서브 윈도우에 표시
	*
	* ══════════════════════════════════════════════════════════════════
	*  Camera 기능 (CaptureManager.Camera 이너 클래스)
	* ══════════════════════════════════════════════════════════════════
	*   cam.start(url)          : MJPEG 스트림 수신 시작
	*   cam.stop()              : 스트림 중지
	*   cam.capture(dir)        : 현재 프레임을 dir/img/cam_*.jpg 저장
	*   cam.isRunning()         : 스트림 실행 여부
	*   cam.getLastFrame()      : 마지막 수신 WritableImage  (씬 주입용)
	*   cam.getLastFrameAWT()   : 마지막 수신 BufferedImage (파일 저장용)
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
                timerLbl.setText("자동 취소까지: " + sec[0] + "초");
                dlg.setTitle("종료 확인 — " + sec[0] + "초 후 취소");
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
		// ── 우선순위: ini 저장값 → 시스템 영역 → 사용자 영역(portable ZIP) ──

		static final String INI_KEY = "npp.exePath";

		/** 시스템 영역 후보 경로 */
		static final String[] SYSTEM_PATHS = {
			"C:\\Program Files\\Notepad++\\notepad++.exe",
			"C:\\Program Files (x86)\\Notepad++\\notepad++.exe"
		};

		/** 사용자 영역 설치 디렉터리: %LOCALAPPDATA%\Notepad++ */
		public static Path getUserInstallDir() {
			return Paths.get(System.getProperty("user.home"), "AppData", "Local", "Notepad++");
		}
		public static Path getUserExePath() {
			return getUserInstallDir().resolve("notepad++.exe");
		}

		/** ini에서 저장된 경로 읽기 */
		public static String getSavedPath() {
			return AppContext.get(INI_KEY, "");
		}

		/** 경로를 ini에 저장 */
		static void savePath(String path) {
			AppContext.set(INI_KEY, path);
			AppContext.save();
			System.out.println("[NppInstall] 경로 저장: " + path);
		}

		/**
		 * 사용 가능한 notepad++.exe 경로 반환.
		 * 우선순위: (1) ini 저장값 → (2) 시스템 영역 → (3) 사용자 영역
		 * 새로 발견 시 ini에 자동 저장.
		 * @return 경로 문자열, 미설치 시 ""
		 */
		public static String getExePath() {
			// 1. ini 저장값
			String saved = getSavedPath();
			if (!saved.isEmpty() && Files.exists(Paths.get(saved))) return saved;

			// 2. 시스템 영역 (Program Files)
			for (String p : SYSTEM_PATHS) {
				if (Files.exists(Paths.get(p))) {
					savePath(p);
					return p;
				}
			}

			// 3. 사용자 영역
			Path userExe = getUserExePath();
			if (Files.exists(userExe)) {
				savePath(userExe.toString());
				return userExe.toString();
			}

			return "";
		}

		/** 설치 여부 확인 (시스템/사용자 영역 모두) */
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

		/** 동기 설치: 사용자 영역에 portable ZIP 배포 (백그라운드 스레드에서 호출) */
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
					"portable ZIP 링크를 찾지 못했습니다.\n수동 설치: https://notepad-plus-plus.org/");

			System.out.println("[NppInstall] URL: " + zipUrl);
			Path tempDir = Files.createTempDirectory("npp_portable_");
			Path zipFile = tempDir.resolve(extractFileName(zipUrl));

			System.out.println("[NppInstall] 다운로드 중...");
			downloadFile(client, zipUrl, zipFile);
			System.out.println("[NppInstall] 다운로드 완료 " + Files.size(zipFile)/1024 + " KB");

			Path installDir = getUserInstallDir();
			Files.createDirectories(installDir);
			System.out.println("[NppInstall] 압축 해제: " + installDir);
			unzip(zipFile, installDir);

			try { Files.deleteIfExists(zipFile); Files.deleteIfExists(tempDir); }
			catch (Exception ignored) {}

			// 설치 후 경로 확인 및 ini 저장
			if (Files.exists(getUserExePath())) {
				savePath(getUserExePath().toString());
				System.out.println("[NppInstall] 완료: " + installDir);
			} else {
				System.out.println("[NppInstall] 설치 후 notepad++.exe 를 찾지 못했습니다.");
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
				throw new IOException("릴리스 페이지 조회 실패: HTTP " + res.statusCode());
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
				throw new IOException("다운로드 실패: HTTP " + res.statusCode());
		}

		// ── JavaFX 연동 ─────────────────────────────────────────────
		public static void installAsync(javafx.stage.Stage owner) {
			new Thread(() -> {
				try {
					String existing = getExePath();
					if (!existing.isEmpty()) {
						showResult(owner, "Notepad++ 설치",
							"\u2705 이미 설치되어 있습니다.\n\n위치: " + existing); return;
					}
					showResult(owner, "Notepad++ 설치",
						"\u23f3 사용자 영역에 Portable 버전을 다운로드합니다.\n\n"
						+ "설치 위치: " + getUserInstallDir() + "\n"
						+ "관리자 권한 불필요 \u00b7 완료 후 결과가 표시됩니다.");
					install();
					String installed = getExePath();
					if (!installed.isEmpty())
						showResult(owner, "Notepad++ 설치",
							"\u2705 설치 완료!\n\n위치: " + installed);
					else
						showResult(owner, "Notepad++ 설치",
							"\u274c 설치 실패\n\n수동 설치: https://notepad-plus-plus.org/downloads/");
				} catch (Exception e) {
					showResult(owner, "Notepad++ 설치", "\u274c 오류: " + e.getMessage());
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
					System.out.println("Notepad++ 이미 설치되어 있습니다.");
					return;
				}
				if (isWingetAvailable()) {
					System.out.println("winget 감지됨. winget으로 설치 시도...");
					int code = installWithWinget();
					System.out.println("winget 종료 코드: " + code);
					if (code == 0 && isAlreadyInstalled()) {
						System.out.println("Notepad++ 설치 완료 (winget)");
						return;
					}
					System.out.println("winget 설치 실패 또는 설치 확인 실패. 직접 다운로드 방식으로 진행...");
					} else {
					System.out.println("winget 없음. 직접 다운로드 방식으로 진행...");
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
				throw new RuntimeException("최신 Notepad++ 설치 파일 링크를 찾지 못했습니다.");
			}
			System.out.println("OS 아키텍처: " + (is64 ? "64-bit" : "32-bit"));
			System.out.println("설치 파일 URL: " + installerUrl);
			Path tempDir = Files.createTempDirectory("npp_install_");
			Path installer = tempDir.resolve(extractFileName(installerUrl));
			downloadFile(client, installerUrl, installer);
			System.out.println("다운로드 완료: " + installer);
			int code = runInstaller(installer);
			System.out.println("설치 종료 코드: " + code);
			if (code == 0 && isAlreadyInstalled()) {
				System.out.println("Notepad++ 설치 완료 (직접 다운로드)");
				} else if (code == 0) {
				System.out.println("설치 명령은 정상 종료했지만 설치 확인은 실패했습니다.");
				} else {
				System.out.println("설치 실패");
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
				throw new IOException("릴리스 페이지 조회 실패: HTTP " + response.statusCode());
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
				throw new IOException("다운로드 실패: HTTP " + response.statusCode());
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
						showResult(owner, "Notepad++ 설치",
						"✅ Notepad++이 이미 설치되어 있습니다.");
						return;
					}
					showResult(owner, "Notepad++ 설치",
						"⏳ Notepad++ 설치를 시작합니다.\n\n"
						+ "winget 또는 직접 다운로드로 설치합니다.\n"
					+ "완료 후 결과 창이 다시 표시됩니다.");
					InstallNotepadPP_Admin(null);
					if (isAlreadyInstalled())
					showResult(owner, "Notepad++ 설치", "✅ Notepad++ 설치가 완료되었습니다.");
					else
					showResult(owner, "Notepad++ 설치",
						"❌ 설치에 실패했습니다.\n"
						+ "관리자 권한으로 실행하거나 수동으로 설치해 주세요.\n"
					+ "https://notepad-plus-plus.org/downloads/");
					} catch (Exception e) {
					showResult(owner, "Notepad++ 설치", "❌ 설치 오류: " + e.getMessage());
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
		/** null 허용 — 캡처 불필요 시 null 전달 가능 */
		private final javafx.scene.Node clockNode;
		/** 여러 이미지 창이 겹치지 않도록 오프셋 순환 */
		private int imageWindowOffset = 0;
		// ── 생성자 ───────────────────────────────────────────────────────
		/**
			* @param clockNode 시계 씬 노드 (captureClockScreen 용). null 가능.
		*/
		public CaptureManager(javafx.scene.Node clockNode) {
			this.clockNode = clockNode;
		}
		// ═══════════════════════════════════════════════════════════════
		//  ScreenCapture 기능 — 화면 캡처 및 이미지 표시
		// ═══════════════════════════════════════════════════════════════
		/**
			* ClockNode 스냅샷을 캡처하여 임시 PNG 파일로 저장.
			* <b>반드시 JavaFX Application Thread 에서 호출해야 한다.</b>
			* 백그라운드 스레드에서 필요하면 Platform.runLater 로 래핑하라.
			*
			* @return 저장된 PNG 파일
		*/
		public File captureClockScreen() throws Exception {
			if (clockNode == null)
			throw new IllegalStateException("clockNode 가 설정되지 않았습니다.");
			WritableImage snapshot = clockNode.snapshot(null, null);
			BufferedImage  awtImg  = SwingFXUtils.fromFXImage(snapshot, null);
			File outFile = new File(System.getProperty("java.io.tmpdir"),
			"clock_capture_" + System.currentTimeMillis() + ".png");
			ImageIO.write(awtImg, "PNG", outFile);
			return outFile;
		}
		/**
			* 모든 모니터를 포함한 전체 화면을 캡처.
			* AWT Robot 을 사용하므로 백그라운드 스레드에서도 호출 가능.
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
			* 특정 모니터를 캡처.
			* @param monitorIndex 0 부터 시작하는 모니터 인덱스
		*/
		public File captureMonitor(int monitorIndex) throws Exception {
			java.awt.GraphicsDevice[] screens =
			java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
			if (monitorIndex >= screens.length)
			throw new Exception("모니터 " + (monitorIndex + 1) + "이 없습니다. "
			+ "(연결된 모니터: " + screens.length + "개)");
			java.awt.Rectangle bounds = screens[monitorIndex].getDefaultConfiguration().getBounds();
			BufferedImage img = new java.awt.Robot().createScreenCapture(bounds);
			File outFile = new File(System.getProperty("java.io.tmpdir"),
			"monitor" + (monitorIndex + 1) + "_" + System.currentTimeMillis() + ".png");
			ImageIO.write(img, "PNG", outFile);
			return outFile;
		}
		
		/**
			* 이미지 파일을 새 JavaFX Stage 서브 윈도우에 표시.
			* 화면 크기의 80% 를 최대 크기로 자동 스케일.
			* 여러 창이 열릴 경우 30px 씩 오프셋하여 겹침 방지.
			* 내부에서 Platform.runLater 를 사용하므로 어느 스레드에서나 호출 가능.
			*
			* @param imageFile 표시할 이미지 파일
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
					System.out.println("[ImageWindow] 표시 실패: " + e.getMessage());
				}
			});
		}
		
		// ═══════════════════════════════════════════════════════════════
		//  Camera — IP Webcam MJPEG 스트림 수신
		//
		//  IP Webcam MJPEG 포맷:
		//    Content-Type: multipart/x-mixed-replace; boundary=--myboundary
		//    각 파트: --myboundary\r\nContent-Type: image/jpeg\r\n\r\n<JPEG>\r\n
		//
		//  저장 파일명: img/cam_yyyyMMdd_HHmmss_SSS.jpg
		//
		//  사용법:
		//    CaptureManager.Camera cam = new CaptureManager.Camera(frameListener);
		//    cam.start("http://192.168.x.x:8080");
		//    cam.stop();
		//    cam.capture(saveDir);
		// ═══════════════════════════════════════════════════════════════
		
		public static class Camera {
			
			/**
				* 새 프레임 도착 시 콜백.
				* WritableImage 는 JavaFX 이미지이므로 FX 씬에 즉시 적용 가능.
				* <b>콜백은 백그라운드(Camera-Reader) 스레드에서 호출된다.</b>
				* FX 씬 노드를 직접 수정하려면 Platform.runLater 를 사용하라.
			*/
			public interface FrameListener {
				void onFrame(WritableImage frame);
			}
			
			private final FrameListener    listener;
			private volatile boolean       running      = false;
			private volatile BufferedImage lastFrameAWT = null;   // 파일 저장용
			private volatile WritableImage lastFrame    = null;   // FX 씬 주입용
			private Thread readerThread;
			
			public Camera(FrameListener listener) {
				this.listener = listener;
			}
			
			public boolean isRunning()             { return running; }
			public boolean isConnected()           { return running && lastFrame != null; }
			/** 마지막 수신 JavaFX 이미지 (FxGPUNeon 배경 주입용) */
			public WritableImage getLastFrame()    { return lastFrame; }
			/** 마지막 수신 AWT 이미지 (파일 저장용) */
			public BufferedImage getLastFrameAWT() { return lastFrameAWT; }
			
			/** MJPEG 스트림 수신 시작 */
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
								+ "), 3초 후 재시도: " + e.getMessage());
								if (failCount >= MAX_FAIL) {
									System.out.println("[Camera] 연속 " + MAX_FAIL + "회 실패 → 자동 중지");
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
				System.out.println("[Camera] 스트림 시작: " + streamUrl);
			}
			
			/** 스트림 중지 */
			public void stop() {
				running = false;
				if (readerThread != null) {
					readerThread.interrupt();
					readerThread = null;
				}
				lastFrame    = null;
				lastFrameAWT = null;
				System.out.println("[Camera] 스트림 중지");
			}
			
			/**
				* 현재 프레임을 saveDir/img/ 폴더에 저장.
				* 파일명: cam_yyyyMMdd_HHmmss_SSS.jpg
				* @return 저장된 파일 경로 (실패 시 null)
			*/
			public String capture(File saveDir) {
				BufferedImage frame = lastFrameAWT;
				if (frame == null) {
					System.out.println("[Camera] 캡처 실패: 수신된 프레임 없음");
					return null;
				}
				try {
					File imgDir = new File(saveDir, "img");
					if (!imgDir.exists()) imgDir.mkdirs();
					
					String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
					File   file = new File(imgDir, "cam_" + ts + ".jpg");
					ImageIO.write(frame, "jpg", file);
					System.out.println("[Camera] 저장 완료: " + file.getAbsolutePath());
					return file.getAbsolutePath();
					} catch (Exception e) {
					System.out.println("[Camera] 저장 오류: " + e.getMessage());
					return null;
				}
			}
			
			// ── MJPEG 스트림 파싱 ───────────────────────────────────────
			
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
