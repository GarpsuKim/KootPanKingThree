import java.net.URI;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
	* AppRestarter - 앱 생명주기 Manage 통합 클래스
	*
	* ── 포함된 구 클래스 ──────────────────────────────────────────
	*   ① AppRestarter    : 앱 재시작 및 AppCDS(JSA) 자동 생성  (기존)
	*   ② WindowsAutoStart: Windows 부팅 자동 실행 등록/해제    (구 WindowsAutoStart.java)
	*   ③ ShutdownGuard   : 종료 신호 감지 → Email+텔레그램 Notice (구 ShutdownGuard.java)
	*
	* ── 외부 참조 변경사항 ────────────────────────────────────────
	*   구 WindowsAutoStart.check()  →  AppRestarter.AutoStart.check()
	*   구 WindowsAutoStart.set(b)   →  AppRestarter.AutoStart.set(b)
	*   구 ShutdownGuard             →  AppRestarter.ShutdownGuard
	*
	* ══════════════════════════════════════════════════════════════
	*  AppRestarter (메인 클래스)
	* ══════════════════════════════════════════════════════════════
	*   ① restartApp()          : Settings Save 후 현재 프로세스 종료 → 자기 자신 재실행
	*   ② buildAppCdsIfNeeded() : jar 환경에서 JSA 아카이브 백그라운드 자동 생성
	*
	* ── 실행 File Path 탐색 우선순위 ────────────────────────────
	*   ① sun.java.command 에 .jar / .exe 가 명시된 경우
	*   ② CodeSource 위치가 .jar / .exe 인 경우
	*   ③ CodeSource Folder(최대 3단계 위)에서 .exe 탐색
	*
	* ── Enable법 ───────────────────────────────────────────────────
	*   AppRestarter restarter = new AppRestarter(gmail, tg);
	*   restarter.setCachedPaths(exePath, javawPath, jsaPath);
	*   restarter.restartApp(saveConfigRunnable);
	*   restarter.buildAppCdsIfNeeded(saveConfigRunnable);
	*
	*   AppRestarter.AutoStart.check();        // 자동 실행 등록 여부
	*   AppRestarter.AutoStart.set(true/false); // 등록/해제
	*
	*   AppRestarter.ShutdownGuard guard = new AppRestarter.ShutdownGuard(gmail, tg);
	*   guard.register();
	*   guard.cancel();
*/
public class AppRestarter {
    // ── 의존성 ────────────────────────────────────────────────
    private final GmailSender       gmail;
    private TelegramBot             tg;
    // ── FX 업그레이드 다이얼로그용 (static: doUpgrade()가 static) ──
    private static javafx.stage.Stage ownerStage   = null;
    private static Runnable           exitCallback = null;
    // ── Path 캐h (INI Save/로드) ────────────────────────────
    private String cachedExePath   = "";
    private String cachedJavawPath = "";
    private String cachedJsaPath   = "";
    // ── 생성자 ───────────────────────────────────────────────
    public AppRestarter(GmailSender gmail, TelegramBot tg) {
        this.gmail = gmail;
        this.tg    = tg;
    }
    /** doUpgrade() 다이얼로그의 owner Stage Settings */
    public static void setOwnerStage(javafx.stage.Stage stage) {
        ownerStage = stage;
    }
    /** Upgrade When done, 앱을 ShutdownOther는 콜백 Settings (Yes: mainWindow::exitAll) */
    public static void setExitCallback(Runnable callback) {
        exitCallback = callback;
    }
    // ── 캐h Path 접근자 ─────────────────────────────────────
    public void setCachedPaths(String exePath, String javawPath, String jsaPath) {
        this.cachedExePath   = exePath   != null ? exePath   : "";
        this.cachedJavawPath = javawPath != null ? javawPath : "";
        this.cachedJsaPath   = jsaPath   != null ? jsaPath   : "";
	}
    public String getCachedExePath()   { return cachedExePath; }
    public String getCachedJavawPath() { return cachedJavawPath; }
    public String getCachedJsaPath()   { return cachedJsaPath; }
    /** TelegramBot 인스턴스를 나중에 연결/교체한다. (main 선생성 후 앱 initialized h 주입용) */
    public synchronized void setTelegramBot(TelegramBot tg) {
        this.tg = tg;
	}
    /** 현재 연결된 TelegramBot 반환 */
    public synchronized TelegramBot getTelegramBot() {
        return tg;
	}
    // ── 공개 API ─────────────────────────────────────────────
    /**
		* 재시작 확인 → Notice 전송 → 새 프로세스 실행 → System.exit(0)
		* @param onBeforeRestart 재시작 직전 실행할 콜백 (Settings Save, ShutdownGuard 취소 등)
	*/
    public void restartApp(Runnable onBeforeRestart) {
        if (onBeforeRestart != null) onBeforeRestart.run();
		restartApp();
	}
    public void restartApp() {
        String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String pcName = getPcName();
        String userId = System.getProperty("user.name", "(unknown)");
        System.out.println("[Restart] 재시작 요청"
		+ " | Time=" + now + " | PC=" + pcName + " | User=" + userId);
        final String tgMsg = "🔄 App Restart\n\n"
		+ "🕐 Time    : " + now    + "\n"
		+ "💻 PC      : " + pcName + "\n"
		+ "👤 User    : " + userId;
        String mailSubject = "🔄 [App Restart] " + pcName;
        String mailBody    = GmailSender.APP_SIGNATURE
		+ "App Restart was requested from the popup menu.\n\n"
		+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
		+ "Time   : " + now    + "\n"
		+ "PC    : " + pcName + "\n"
		+ "User   : " + userId + "\n"
		+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        Runnable doRestart = buildRestartRunnable(tgMsg);
        if (gmail != null && gmail.isConfigured() && !gmail.lastTo.isEmpty()) {
            gmail.sendShutdownNotice(doRestart, mailSubject, mailBody);
			} else {
            new Thread(doRestart, "RestartProc").start();
		}
	}
    /**
		* AppCDS JSA 아카이브를 백그라운드에서 자동 생성.
		* jar 실행 환경에서만 동작. JSA 가 이미 존재Other면 캐시만 갱신 후 스킵.
		* @param saveConfig JSA Path 갱신 후 INI Save을 위한 콜백
	*/
    public void buildAppCdsIfNeeded(Runnable saveConfig) {
        String jarPath = cachedExePath;
        if (jarPath.isEmpty()) {
            try {
                java.security.CodeSource cs = getClass().getProtectionDomain().getCodeSource();
                if (cs != null) {
                    String p = cs.getLocation().toURI().getPath();
                    if (p != null && p.endsWith(".jar"))
					jarPath = new java.io.File(p).getAbsolutePath();
				}
			} catch (Exception ignored) {}
		}
        if (jarPath.isEmpty() || !jarPath.endsWith(".jar")) return;
        java.io.File jarFile = new java.io.File(jarPath);
        String jsaPath = new java.io.File(jarFile.getParentFile(),
		jarFile.getName().replace(".jar", ".jsa")).getAbsolutePath();
        if (new java.io.File(jsaPath).exists()) {
            if (!jsaPath.equals(cachedJsaPath)) {
                cachedJsaPath = jsaPath;
                if (saveConfig != null) saveConfig.run();
                System.out.println("[AppCDS] 기존 JSA Enable: " + jsaPath);
			}
            return;
		}
        String javaw = cachedJavawPath;
        if (javaw.isEmpty()) {
            javaw = System.getProperty("java.home") + java.io.File.separator
			+ "bin" + java.io.File.separator + "javaw";
		}
        if (javaw.toLowerCase().contains("runtime" + java.io.File.separator + "bin")) {
            String sysJavaw = findSystemJavaw();
            if (sysJavaw != null) {
                System.out.println("[AppCDS] jpackage javaw 감지 → 시스템 javaw Enable: " + sysJavaw);
                javaw = sysJavaw;
				} else {
                System.out.println("[AppCDS] 시스템 javaw 탐색 Failed - JSA 생성 스킵");
                return;
			}
		}
        final String fJavaw = javaw;
        final String fJar   = jarPath;
        final String fJsa   = jsaPath;
        new Thread(() -> {
            try {
                System.out.println("[AppCDS] JSA 생성 Start: " + fJsa);
                ProcessBuilder pb = new ProcessBuilder(
                    fJavaw, "-Xshare:dump",
                    "-XX:SharedArchiveFile=" + fJsa,
				"-jar", fJar);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
                p.destroyForcibly();
                if (new java.io.File(fJsa).exists()) {
                    cachedJsaPath = fJsa;
                    if (saveConfig != null) saveConfig.run();
                    System.out.println("[AppCDS] JSA 생성 Done: " + fJsa);
					} else {
                    System.out.println("[AppCDS] JSA 생성 Failed (File not found)");
				}
				} catch (Exception e) {
                System.out.println("[AppCDS] JSA 생성 오류: " + e.getMessage());
			}
		}, "AppCDS-Builder").start();
	}
    // ── 내부: 재시작 Runnable ────────────────────────────────
    private Runnable buildRestartRunnable(String tgMsg) {
        return () -> {
			/*
				if (tg != null && tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty()) {
                try {
				Thread tgThread = new Thread(() -> tg.sendTelegram(tg.myChatId, tgMsg), "RestartTG");
				tgThread.start();
				tgThread.join(10000);
				System.out.println("[Restart] 텔레그램 전송 Done");
                } catch (Exception e) {
				System.out.println("[Restart] 텔레그램 전송 Failed: " + e.getMessage());
                }
				}
			*/
            try {
                String exePath = cachedExePath;
                if (exePath.isEmpty() || !new java.io.File(exePath).exists()) {
                    System.out.println("[Restart] Path 캐h 없음 - 탐색 시작");
                    exePath = resolveExePathForRestart();
					} else {
                    System.out.println("[Restart] 캐시된 Path Enable: " + exePath);
				}
                if (exePath == null) {
                    System.out.println("[Restart] 실행 File Path 탐색 Failed , Restart Failed");
                    return;
				}
                if (!exePath.endsWith(".exe")) {
                    java.io.File jarDir = new java.io.File(exePath).getParentFile();
                    outer:
                    for (int up = 0; up < 3; up++) {
                        if (jarDir == null) break;
                        java.io.File[] exeFiles = jarDir.listFiles(
						c -> c.getName().toLowerCase().endsWith(".exe") && c.isFile());
                        if (exeFiles != null) {
                            for (java.io.File ef : exeFiles) {
                                String n = ef.getName().toLowerCase();
                                if (n.contains("kootpanking")) {
                                    exePath = ef.getAbsolutePath();
                                    System.out.println("[Restart] jar→exe 전환: " + exePath);
                                    break outer;
								}
							}
                            if (exeFiles.length > 0) {
                                exePath = exeFiles[0].getAbsolutePath();
                                System.out.println("[Restart] jar→exe 전환(첫번째): " + exePath);
                                break;
							}
						}
                        jarDir = jarDir.getParentFile();
					}
				}
                cachedExePath = exePath;
                ProcessBuilder pb;
                if (exePath.endsWith(".exe")) {
                    pb = new ProcessBuilder(exePath);
					} else {
                    String javaw = cachedJavawPath;
                    boolean javawExists = !javaw.isEmpty()
					&& (new java.io.File(javaw).exists()
					|| new java.io.File(javaw + ".exe").exists());
                    if (!javawExists) {
                        javaw = System.getProperty("java.home") + java.io.File.separator
						+ "bin" + java.io.File.separator + "javaw";
                        cachedJavawPath = javaw;
                        System.out.println("[Restart] javaw Path 탐색: " + javaw);
						} else {
                        System.out.println("[Restart] 캐시된 javaw Enable: " + javaw);
					}
                    pb = new ProcessBuilder(javaw, "-jar", exePath);
                    if (!cachedJsaPath.isEmpty() && new java.io.File(cachedJsaPath).exists()) {
                        pb = new ProcessBuilder(javaw,
                            "-XX:SharedArchiveFile=" + cachedJsaPath,
						"-jar", exePath);
                        System.out.println("[Restart] AppCDS JSA Apply: " + cachedJsaPath);
					}
				}
                System.out.println("[Restart] INI 캐h exePath=" + cachedExePath
				+ (cachedJavawPath.isEmpty() ? "" : " javawPath=" + cachedJavawPath));
                pb.directory(new java.io.File(exePath).getParentFile());
                pb.start();
                System.out.println("[Restart] 새 프로세스 시작 Done: " + exePath);
                System.exit(0);
				} catch (Exception ex) {
                System.out.println("[Restart] 재시작 Failed: " + ex.getMessage());
			}
		};
	}
    // ── 내부: Path 탐색 ─────────────────────────────────────
	String resolveExePathForRestart() {
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = sc.split("\\s+")[0];
            if (first.endsWith(".jar") || first.endsWith(".exe")) {
                java.io.File f = new java.io.File(first).getAbsoluteFile();
                if (f.exists()) return f.getAbsolutePath();
			}
		} catch (Exception ignored) {}
        java.io.File csDir = null;
        try {
            java.io.File f = new java.io.File(
                AppRestarter.class.getProtectionDomain()
			.getCodeSource().getLocation().toURI()).getAbsoluteFile();
            if ((f.getName().endsWith(".jar") || f.getName().endsWith(".exe")) && f.exists()) {
                return f.getAbsolutePath();
			}
            csDir = f.isDirectory() ? f : f.getParentFile();
		} catch (Exception ignored) {}
        java.io.File dir = csDir;
        for (int up = 0; up < 4; up++) {
            if (dir == null) break;
            java.io.File[] exeFiles = dir.listFiles(
			child -> child.getName().toLowerCase().endsWith(".exe") && child.isFile());
            if (exeFiles != null && exeFiles.length >= 1) {
                for (java.io.File ef : exeFiles) {
                    String n = ef.getName().toLowerCase();
                    if (n.contains("kootpanking")) {
                        System.out.println("[Restart] exe 탐색(이름매칭): " + ef.getAbsolutePath());
                        return ef.getAbsolutePath();
					}
				}
                System.out.println("[Restart] exe 탐색(첫번째): " + exeFiles[0].getAbsolutePath());
                return exeFiles[0].getAbsolutePath();
			}
            dir = dir.getParentFile();
		}
        return null;
	}
	static String getSelfJarPath() {
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            if (sc.endsWith(".jar"))
			return new java.io.File(sc).getAbsolutePath();
		} catch (Exception ignored) {}
        try {
            return new java.io.File(AppRestarter.class.getProtectionDomain()
			.getCodeSource().getLocation().toURI()).getAbsolutePath();
		} catch (Exception ignored) {}
        return null;
	}
	String findSystemJavaw() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            java.io.File f = new java.io.File(javaHome, "bin" + java.io.File.separator + "javaw.exe");
            if (f.exists()) return f.getAbsolutePath();
		}
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                java.io.File f = new java.io.File(dir.trim(), "javaw.exe");
                if (f.exists() && !f.getAbsolutePath().toLowerCase().contains("runtime")) {
                    return f.getAbsolutePath();
				}
			}
		}
        String[] candidates = {
            "C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft", "C:\\Program Files\\Liberica"
		};
        for (String base : candidates) {
            java.io.File baseDir = new java.io.File(base);
            if (!baseDir.exists()) continue;
            java.io.File[] jdks = baseDir.listFiles(
			f -> f.isDirectory() && f.getName().toLowerCase().startsWith("jdk"));
            if (jdks == null) continue;
            java.util.Arrays.sort(jdks, java.util.Comparator.comparing(java.io.File::getName).reversed());
            for (java.io.File jdk : jdks) {
                java.io.File f = new java.io.File(jdk, "bin" + java.io.File.separator + "javaw.exe");
                if (f.exists()) return f.getAbsolutePath();
			}
		}
        return null;
	}
    private  String getPcName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "(unknown)"; }
	}
    // ═══════════════════════════════════════════════════════════
    //  AutoStart — Windows 부팅 자동 실행 등록/해제 (구 WindowsAutoStart.java)
    //
    //  HKCU\Software\Microsoft\Windows\CurrentVersion\Run 레지스트리 키에
    //  reg.exe 를 통해 앱 실행 명령을 등록/해제한다.
    //
    //  Enable법:
    //    boolean on = AppRestarter.AutoStart.check();
    //    boolean ok = AppRestarter.AutoStart.set(true);   // 등록
    //    boolean ok = AppRestarter.AutoStart.set(false);  // 해제
    // ═══════════════════════════════════════════════════════════
    public  class AutoStart {
        private static final String REG_KEY  = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        private static final String REG_NAME = "KootPanKingThree";
        /** 현재 Auto-start Register 여부 OK */
        public static boolean check() {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
				"reg", "query", REG_KEY, "/v", REG_NAME });
                return p.waitFor() == 0;
			} catch (Exception e) { return false; }
		}
        /**
			* 자동 실행 등록(enable=true) 또는 해제(enable=false).
			* @return 성공 여부
		*/
        public static boolean set(boolean enable) {
            try {
                String reg = System.getenv("SystemRoot") + "\\System32\\reg.exe";
                ProcessBuilder pb;
                if (enable) {
                    String cmdValue = buildCmdValue();
                    if (cmdValue == null) return false;
                    System.out.println("[AutoStart] 등록: " + cmdValue);
                    pb = new ProcessBuilder(
                        reg, "add", REG_KEY,
                        "/v", REG_NAME,
                        "/t", "REG_SZ",
                        "/d", cmdValue,
					"/f");
					} else {
                    pb = new ProcessBuilder(
                        reg, "delete", REG_KEY,
                        "/v", REG_NAME,
					"/f");
				}
                pb.redirectErrorStream(true);
                Process p = pb.start();
                new Thread(() -> {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
					new java.io.InputStreamReader(p.getInputStream(), "CP949"))) {
					br.lines().forEach(l -> System.out.println("[AutoStart] " + l));
                    } catch (Exception ignored) {}
				}).start();
                int exit = p.waitFor();
                System.out.println("[AutoStart] exit = " + exit);
                return exit == 0;
				} catch (Exception e) {
                System.out.println("[AutoStart] 오류: " + e.getMessage());
                return false;
			}
		}
        /**
			* 레지스트리에 등록할 실행 명령 문자열 생성.
			* jpackage exe → exe Path / jar → javaw -jar <path>
		*/
        private static String buildCmdValue() {
            String exePath = ProcessHandle.current().info().command().orElse(null);
            if (exePath != null
				&& exePath.toLowerCase().endsWith(".exe")
				&& !exePath.toLowerCase().contains("javaw")
				&& !exePath.toLowerCase().contains("java")) {
                System.out.println("[AutoStart] exe 모드 등록: " + exePath);
                return exePath;
			}
            String jarPath = AppRestarter.getSelfJarPath();
            String javaw   = System.getProperty("java.home")
			+ java.io.File.separator + "bin"
			+ java.io.File.separator + "javaw.exe";
            if (jarPath != null && jarPath.endsWith(".jar")) {
                System.out.println("[AutoStart] jar 모드 등록: " + javaw + " -jar " + jarPath);
                return javaw + " -jar " + jarPath;
				} else if (jarPath != null) {
                return javaw + " -cp " + jarPath + " KootPanKing";
			}
            return null;
		}
	}
    // ═══════════════════════════════════════════════════════════
    //  ShutdownGuard — 종료 신호 감지 → Email+텔레그램 Notice (구 ShutdownGuard.java)
    //
    //  Shutdown Hook 1개로 모든 케이스를 처리한다.
    //   ✅ Windows 종료/재시작/로그아웃 (JVM 에 SIGTERM 유사 신호 전달)
    //   ✅ kill PID (SIGTERM), Ctrl+C (SIGINT), System.exit()
    //   ❌ kill -9 (SIGKILL) — OS 즉h 강제종료, 어떤 방법으로도 불가
    //
    //  Enable법:
    //    AppRestarter.ShutdownGuard guard = new AppRestarter.ShutdownGuard(gmail, tg);
    //    guard.register();   // main 또는 생성자에서 1회 호출
    //    guard.cancel();     // 정상 종료(메뉴 → EXIT) 전에 호출 → Notice 생략
    //    guard.resume();     // cancel() 을 되돌림
    // ═══════════════════════════════════════════════════════════
    public static class ShutdownGuard {
        private final GmailSender gmail;
        private final TelegramBot tg;
        /** true = cancel() 호출됨 → Hook 실행 h Notice 생략 */
        private volatile boolean cancelled = false;
        /** Shutdown Hook 스레드 (중복 Register 방지) */
        private Thread hookThread = null;
        public ShutdownGuard(GmailSender gmail, TelegramBot tg) {
            this.gmail = gmail;
            this.tg    = tg;
		}
        /** Shutdown Hook Register. 앱 All에서 1회만 호출한다. */
        public synchronized void register() {
            if (hookThread != null) return;
            hookThread = new Thread(() -> {
                if (cancelled) {
                    System.out.println("[ShutdownGuard] 정상 종료 — Notice 생략");
                    return;
				}
                System.out.println("[ShutdownGuard] 종료 신호 감지 — Notice 전송 시작");
                sendNotifications();
                System.out.println("[ShutdownGuard] Done");
                // AppLogger.close();
			}, "ShutdownGuard-Hook");
            Runtime.getRuntime().addShutdownHook(hookThread);
            System.out.println("[ShutdownGuard] 등록 Done");
		}
        /** 정상 Shutdown h 호출 — Notice을 보내지 않는다. */
        public void cancel()  { cancelled = true;  System.out.println("[ShutdownGuard] Notice Cancel (정상 Shutdown)"); }
        /** cancel() 을 되돌린다. */
        public void resume()  { cancelled = false; }
        private void sendNotifications() {
            String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String pcName = System.getenv("COMPUTERNAME");   // Windows();
            String userId = System.getProperty("user.name", "(unknown)");
            Thread tgThread   = new Thread(() -> sendTelegramExit(), "SG-Telegram");
            Thread mailThread = new Thread(() -> sendEmail(now, pcName, userId),    "SG-Email");
            tgThread.start();
            mailThread.start();
            try { tgThread.join(4000); }   catch (InterruptedException ignored) {}
            try { mailThread.join(4000); } catch (InterruptedException ignored) {}
		}
        private void sendTelegramExit() {
			if ( tg == null ) return;
			try {
                tg.sendTelegramExit();
                System.out.println("[ShutdownGuard] 텔레그램 전송 Done");
				} catch (Exception e) {
                System.out.println("[ShutdownGuard] 텔레그램 전송 Failed: " + e.getMessage());
			}
		}
        private void sendEmail(String now, String pcName, String userId) {
            if (gmail == null || !gmail.isConfigured() || gmail.lastTo.isEmpty()) return;
            try {
                String subject = "⚠️ [Force Shutdown Detected] " + pcName;
                String body    = GmailSender.APP_SIGNATURE
				+ "Process was shut down by Windows Shutdown/Restart or external signal.\n\n"
				+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
				+ "Detected: " + now    + "\n"
				+ "PC Name: " + pcName + "\n"
				+ "User   : " + userId + "\n"
				+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
				+ "(kill -9 / SIGKILL cannot be detected)";
				// ── 첨부 File 수집: 마스터 ini + 로그 File ──────────
				final java.util.List<java.io.File> attachFiles = new java.util.ArrayList<>();
				java.io.File iniFile = new java.io.File(AppContext.CONFIG_FILE);
				if (iniFile.exists()) attachFiles.add(iniFile);
				String logPath = AppLogger.getLogFilePath();
				if (logPath != null && !logPath.isEmpty()) {
					java.io.File logFile = new java.io.File(logPath);
					if (logFile.exists()) attachFiles.add(logFile);
				}
				
                gmail.sendOneSmtpWithAttachments(gmail.from, gmail.pass, gmail.lastTo, subject, body, attachFiles);
                System.out.println("[ShutdownGuard] 이Sending email Done");
				} catch (Exception e) {
                System.out.println("[ShutdownGuard] 이Sending email Failed: " + e.getMessage());
			}
		}
        private static String getPcName() {
            try { return java.net.InetAddress.getLocalHost().getHostName(); }
            catch (Exception e) { return "(unknown)"; }
		}
	}
    // ═══════════════════════════════════════════════════════════
    //  ToolManager (내부 static 클래스)
    //  yt-dlp / ffmpeg 자동 Download 및 Path 탐색
    //  외부 참조: AppRestarter.ToolManager.init(appDir)
    //             AppRestarter.ToolManager.resolveExe(appDir, exeName)
    // ═══════════════════════════════════════════════════════════
    public static class ToolManager {
        // =========================
        // 진입점 — 반드h 백그라운드 스레드에서 호출
        // =========================
        public static void init(String appDir) {
            String toolsDir = toolsDir(appDir);
            new Thread(() -> {
                try {
                    java.nio.file.Files.createDirectories(java.nio.file.Paths.get(toolsDir));
                    ensureYtDlp(toolsDir);
                    ensureFfmpeg(toolsDir);
                    System.out.println("[ToolManager] initialization Done");
					} catch (Exception e) {
                    System.err.println("[ToolManager] initialization Failed: " + e.getMessage());
                    e.printStackTrace();
				}
			}, "ToolManager-Init").start();
		}
        /**
			* exe Path 탐색: appDir/tools/ → PATH 순.
			* KootPanKing.resolveExe() 를 대체.
		*/
        public static String resolveExe(String appDir, String exeName) {
            java.io.File t = new java.io.File(toolsDir(appDir), exeName);
            if (t.exists()) return t.getAbsolutePath();
            String path = System.getenv("PATH");
            if (path != null) {
                for (String dir : path.split(java.io.File.pathSeparator)) {
                    java.io.File c = new java.io.File(dir, exeName);
                    if (c.exists()) return c.getAbsolutePath();
				}
			}
            return exeName;
		}
        private static String toolsDir(String appDir) {
            return appDir + "tools";
		}
        // =========================
        // yt-dlp Download
        // =========================
        private static void ensureYtDlp(String toolsDir) throws Exception {
            java.nio.file.Path exe = java.nio.file.Paths.get(toolsDir, "yt-dlp.exe");
            if (java.nio.file.Files.exists(exe)) {
                System.out.println("[ToolManager] yt-dlp 이미 존재");
                return;
			}
            System.out.println("[ToolManager] yt-dlp Downloading...");
            downloadFile(
			"https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", exe);
            System.out.println("[ToolManager] yt-dlp Download Done");
		}
        // =========================
        // ffmpeg Download + 추출
        // =========================
        private static void ensureFfmpeg(String toolsDir) throws Exception {
            java.nio.file.Path exe = java.nio.file.Paths.get(toolsDir, "ffmpeg.exe");
            if (java.nio.file.Files.exists(exe)) {
                System.out.println("[ToolManager] ffmpeg 이미 존재");
                return;
			}
            System.out.println("[ToolManager] ffmpeg Downloading... (100MB+, 시간 소요)");
            java.nio.file.Path zipPath = java.nio.file.Paths.get(toolsDir, "ffmpeg.zip");
            try {
                downloadFile(
				"https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", zipPath);
                System.out.println("[ToolManager] ffmpeg 압축 해제 중...");
                unzip(zipPath.toString(), toolsDir);
                java.nio.file.Files.walk(java.nio.file.Paths.get(toolsDir))
				.filter(p -> p.getFileName().toString().equalsIgnoreCase("ffmpeg.exe")
				&& !p.equals(exe))
				.findFirst()
				.ifPresent(found -> {
					try {
						java.nio.file.Files.copy(found, exe,
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						System.out.println("[ToolManager] ffmpeg.exe 복사 Done: " + found);
					} catch (java.io.IOException e) { e.printStackTrace(); }
				});
                java.nio.file.Files.walk(java.nio.file.Paths.get(toolsDir), 1)
				.filter(p -> !p.equals(java.nio.file.Paths.get(toolsDir))
					&& java.nio.file.Files.isDirectory(p)
				&& p.getFileName().toString().startsWith("ffmpeg-"))
				.forEach(dir -> {
					try {
						deleteRecursively(dir);
						System.out.println("[ToolManager] 임h Folder Delete: " + dir);
					} catch (java.io.IOException e) { e.printStackTrace(); }
				});
                System.out.println("[ToolManager] ffmpeg Ready Done");
				} finally {
                java.nio.file.Files.deleteIfExists(zipPath);
			}
		}
        // =========================
        // File Download (진행률 출력)
        // =========================
        private static void downloadFile(String urlStr, java.nio.file.Path target) throws Exception {
            java.net.URLConnection conn = java.net.URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            long total = conn.getContentLengthLong();
            try (java.io.InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                long downloaded = 0; int len, lastPct = -1;
                try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(target,
					java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
					downloaded += len;
					if (total > 0) {
						int pct = (int)(downloaded * 100 / total);
						if (pct != lastPct && pct % 10 == 0) {
							System.out.printf("[ToolManager] %s ... %d%%%n",
							target.getFileName(), pct);
							lastPct = pct;
						}
					}
				}
                }
			}
		}
        // =========================
        // ZIP 해제 (zip slip 방어)
        // =========================
        private static void unzip(String zipFile, String destDir) throws Exception {
            java.io.File destDirFile = new java.io.File(destDir).getCanonicalFile();
            byte[] buffer = new byte[8192];
            try (java.util.zip.ZipInputStream zis =
				new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    java.io.File newFile =
					new java.io.File(destDirFile, entry.getName()).getCanonicalFile();
                    if (!newFile.getCanonicalPath().startsWith(
					destDirFile.getCanonicalPath() + java.io.File.separator))
					throw new SecurityException("Zip slip blocked: " + entry.getName());
                    if (entry.isDirectory()) {
                        newFile.mkdirs();
						} else {
                        newFile.getParentFile().mkdirs();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
						}
					}
                    zis.closeEntry();
				}
			}
		}
        // =========================
        // Folder 재귀 Delete
        // =========================
        private static void deleteRecursively(java.nio.file.Path path) throws java.io.IOException {
            java.nio.file.Files.walk(path)
			.sorted(java.util.Comparator.reverseOrder())
			.map(java.nio.file.Path::toFile)
			.forEach(java.io.File::delete);
		}
	}
	//  ■■■■■■■■■■■■■■■■■■■■■■■■■■■■
	public static class PCShortcut {
		private static final int ICON_SIZE = 8;
		private static final int ICON_CACHE_MAX = 512;
		private final java.util.Map<String, javafx.scene.image.Image> iconCache =
        new java.util.LinkedHashMap<String, javafx.scene.image.Image>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
				java.util.Map.Entry<String, javafx.scene.image.Image> eldest) {
                return size() > ICON_CACHE_MAX;
			}
		};
		private final java.io.File iconPngCacheDir = new java.io.File(
		System.getProperty("java.io.tmpdir"), "kpk_launcher_icon_cache");
		public static class AppEntry {
			public final String name;
			public final File file;
			public final String execTarget;
			public final String groupType; // "GENERAL_APP" / "CONTROL_PANEL"
			public AppEntry(String name, File file, String execTarget, String groupType) {
				this.name = name != null ? name : "";
				this.file = file;
				this.execTarget = execTarget != null ? execTarget : "";
				this.groupType = groupType != null ? groupType : "";
			}
			public String sortKey() {
				if (file != null) {
					return file.getName().toLowerCase(java.util.Locale.ROOT);
				}
				return name.toLowerCase(java.util.Locale.ROOT);
			}
			public String cacheKey() {
				if (file != null) return file.getAbsolutePath();
				if (!execTarget.isEmpty()) return execTarget;
				return groupType + "|" + name;
			}
		}
		// ── Favorites 콜백 ─────────────────────────────────────
		public interface FavoriteCallback {
			/** name: lnk File명(확장자 제외), path: 타겟 full path */
			void addFavorite(String name, String path);
		}
		public FavoriteCallback favoriteCallback;
		public PCShortcut() {}
		public javafx.scene.control.Menu createMenuWithWindow(javafx.stage.Stage owner) {
			javafx.scene.control.Menu menu = createMenu();
			menu.getItems().add(0, new javafx.scene.control.SeparatorMenuItem());
			javafx.scene.control.MenuItem openWin =
			new javafx.scene.control.MenuItem("🪟 Open in App Window");
			openWin.setOnAction(e -> showWindow(owner));
			menu.getItems().add(0, openWin);
			return menu;
		}
		public javafx.scene.control.Menu createMenu() {
			javafx.scene.control.Menu rootMenu = new javafx.scene.control.Menu("Desktop");
			javafx.scene.control.Menu normalAppsMenu = new javafx.scene.control.Menu("General Apps");
			javafx.scene.control.Menu windowsAppsMenu = new javafx.scene.control.Menu("Windows Apps");
			rootMenu.getItems().add(normalAppsMenu);
			rootMenu.getItems().add(windowsAppsMenu);
			javafx.scene.control.MenuItem refreshNormal =
            new javafx.scene.control.MenuItem("Refresh");
			refreshNormal.setOnAction(e -> {
				clearIconCache();
				refreshNormalAppsMenu(normalAppsMenu);
			});
			normalAppsMenu.getItems().add(refreshNormal);
			normalAppsMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
			normalAppsMenu.setOnShowing(e -> refreshNormalAppsMenu(normalAppsMenu));
			refreshNormalAppsMenu(normalAppsMenu);
			javafx.scene.control.MenuItem refreshWindows =
            new javafx.scene.control.MenuItem("Refresh");
			refreshWindows.setOnAction(e -> {
				clearIconCache();
				refreshWindowsAppsMenu(windowsAppsMenu);
			});
			windowsAppsMenu.getItems().add(refreshWindows);
			windowsAppsMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
			windowsAppsMenu.setOnShowing(e -> refreshWindowsAppsMenu(windowsAppsMenu));
			refreshWindowsAppsMenu(windowsAppsMenu);
			return rootMenu;
		}
		// ─────────────────────────────────────────────
		// [ MainWindow_Menu / Desktop / General Apps ]
		// ─────────────────────────────────────────────
		private void refreshNormalAppsMenu(javafx.scene.control.Menu normalAppsMenu) {
			if (normalAppsMenu == null) return;
			if (normalAppsMenu.getItems().size() > 2) {
				normalAppsMenu.getItems().remove(2, normalAppsMenu.getItems().size());
			}
			java.util.List<AppEntry> entries = collectGeneralApps();
			if (entries.isEmpty()) {
				javafx.scene.control.MenuItem empty =
                new javafx.scene.control.MenuItem("(none)");
				empty.setDisable(true);
				normalAppsMenu.getItems().add(empty);
				return;
			}
			java.util.Map<String, java.util.List<AppEntry>> grouped = groupByAlphabet(entries);
			for (java.util.Map.Entry<String, java.util.List<AppEntry>> group : grouped.entrySet()) {
				javafx.scene.control.Menu groupMenu =
                new javafx.scene.control.Menu(group.getKey());
				for (AppEntry app : group.getValue()) {
					javafx.scene.control.MenuItem item =
                    new javafx.scene.control.MenuItem(app.name);
					item.setGraphic(createIconView(app));
					item.setOnAction(e -> openEntry(app));
					groupMenu.getItems().add(item);
				}
				normalAppsMenu.getItems().add(groupMenu);
			}
		}
		private java.util.List<AppEntry> collectGeneralApps() {
			java.util.List<File> lnkFiles = new java.util.ArrayList<>();
			String userHome   = System.getProperty("user.home");
			String programData = System.getenv("PROGRAMDATA");
			if (programData == null || programData.isBlank())
			programData = "C:\\ProgramData";
			java.nio.file.Path commonStartMenu =
			java.nio.file.Paths.get(programData,
			"Microsoft", "Windows", "Start Menu");
			java.nio.file.Path commonPrograms =
			java.nio.file.Paths.get(programData,
			"Microsoft", "Windows", "Start Menu", "Programs");
			java.nio.file.Path userStartMenu =
			userHome == null || userHome.isBlank() ? null
			: java.nio.file.Paths.get(
			userHome, "AppData", "Roaming", "Microsoft", "Windows", "Start Menu");
			java.nio.file.Path userPrograms =
			userHome == null || userHome.isBlank() ? null
			: java.nio.file.Paths.get(
			userHome, "AppData", "Roaming", "Microsoft", "Windows", "Start Menu", "Programs");
			// General Apps 원본 수집
			lnkFiles.addAll(listLnkFilesShallow(commonStartMenu));
			lnkFiles.addAll(listLnkFilesShallow(commonPrograms));
			lnkFiles.addAll(listLnkFilesRecursive(commonPrograms));
			lnkFiles.addAll(listLnkFilesShallow(userStartMenu));
			lnkFiles.addAll(listLnkFilesShallow(userPrograms));
			lnkFiles.addAll(listLnkFilesRecursive(userPrograms));
			// File명.lnk 기준 정렬
			lnkFiles.sort(java.util.Comparator.comparing(
				f -> f.getName().toLowerCase(java.util.Locale.ROOT),
				java.lang.String.CASE_INSENSITIVE_ORDER
			));
			// General Apps 내부 중복 제거
			java.util.Map<String, File> dedupGeneral = new java.util.LinkedHashMap<>();
			for (File f : lnkFiles) {
				String key = f.getName().toLowerCase(java.util.Locale.ROOT);
				dedupGeneral.putIfAbsent(key, f);
			}
			// 시스템 앱과 비교용 키셋 생성
			java.util.Set<String> systemKeys = new java.util.HashSet<>();
			for (AppEntry sys : collectWindowsApps()) {
				if (sys != null && sys.file != null) {
					systemKeys.add(sys.file.getName().toLowerCase(java.util.Locale.ROOT));
				}
			}
			// 시스템 앱과 비교용 키셋 생성 (수정)
			/*
				java.util.Set<String> systemKeys = new java.util.HashSet<>();
				for (AppEntry sys : collectWindowsApps()) {
				if (sys == null) continue;
				String key;
				if (sys.file != null) {
				key = sys.file.getName();
				} else {
				key = sys.name;
				}
				if (key != null && !key.isBlank()) {
				systemKeys.add(key.toLowerCase(java.util.Locale.ROOT));
				}
				}
			*/
			// 시스템과 중복되는 General Apps 제거
			java.util.List<AppEntry> result = new java.util.ArrayList<>();
			for (java.util.Map.Entry<String, File> e : dedupGeneral.entrySet()) {
				if (systemKeys.contains(e.getKey())) {
					continue;
				}
				File f = e.getValue();
				String lnkTarget = parseLnkTarget(f);
				String execTarget = (lnkTarget != null && !lnkTarget.isEmpty())
				? lnkTarget : f.getAbsolutePath();
				result.add(new AppEntry(
					toDisplayName(f),
					f,
					execTarget,
					"GENERAL_APP"
				));
			}
			return result;
		}
		private java.util.List<AppEntry> collectGeneralApps___() {
			java.util.List<File> lnkFiles = new java.util.ArrayList<>();
			String userHome = System.getProperty("user.home");
			java.nio.file.Path commonStartMenu =
			java.nio.file.Paths.get("C:\\ProgramData\\Microsoft\\Windows\\Start Menu");
			java.nio.file.Path commonPrograms =
			java.nio.file.Paths.get("C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs");
			java.nio.file.Path userStartMenu =
			userHome == null || userHome.isBlank()
            ? null
            : java.nio.file.Paths.get(
			userHome, "AppData", "Roaming", "Microsoft", "Windows", "Start Menu");
			java.nio.file.Path userPrograms =
			userHome == null || userHome.isBlank()
            ? null
            : java.nio.file.Paths.get(
			userHome, "AppData", "Roaming", "Microsoft", "Windows", "Start Menu", "Programs");
			// 1) C:\ProgramData\Microsoft\Windows\Start Menu 의 모든 lnk
			lnkFiles.addAll(listLnkFilesShallow(commonStartMenu));
			// 2) C:\ProgramData\Microsoft\Windows\Start Menu\Programs 의 모든 lnk
			lnkFiles.addAll(listLnkFilesShallow(commonPrograms));
			// 3) C:\ProgramData\Microsoft\Windows\Start Menu\Programs 재귀
			lnkFiles.addAll(listLnkFilesRecursive(commonPrograms));
			// 4) C:\Users\Enable자명\AppData\Roaming\Microsoft\Windows\Start Menu 의 모든 lnk
			lnkFiles.addAll(listLnkFilesShallow(userStartMenu));
			// 5) C:\Users\Enable자명\AppData\Roaming\Microsoft\Windows\Start Menu\Programs 의 모든 lnk
			lnkFiles.addAll(listLnkFilesShallow(userPrograms));
			// 6) C:\Users\Enable자명\AppData\Roaming\Microsoft\Windows\Start Menu\Programs 재귀
			lnkFiles.addAll(listLnkFilesRecursive(userPrograms));
			// 7) File명.lnk 기준 sort
			lnkFiles.sort(java.util.Comparator.comparing(
				f -> f.getName().toLowerCase(java.util.Locale.ROOT),
				java.lang.String.CASE_INSENSITIVE_ORDER
			));
			// 8) File명.lnk 기준 중복 제거
			java.util.Map<String, File> dedup = new java.util.LinkedHashMap<>();
			for (File f : lnkFiles) {
				String key = f.getName().toLowerCase(java.util.Locale.ROOT);
				dedup.putIfAbsent(key, f);
			}
			java.util.List<AppEntry> result = new java.util.ArrayList<>();
			for (File f : dedup.values()) {
				result.add(new AppEntry(
					toDisplayName(f),
					f,
					f.getAbsolutePath(),
					"GENERAL_APP"
				));
			}
			return result;
		}
		private void refreshWindowsAppsMenu(javafx.scene.control.Menu windowsAppsMenu) {
			if (windowsAppsMenu == null) return;
			if (windowsAppsMenu.getItems().size() > 2) {
				windowsAppsMenu.getItems().remove(2, windowsAppsMenu.getItems().size());
			}
			java.util.List<AppEntry> entries = collectWindowsApps();
			if (entries.isEmpty()) {
				javafx.scene.control.MenuItem empty =
				new javafx.scene.control.MenuItem("(none)");
				empty.setDisable(true);
				windowsAppsMenu.getItems().add(empty);
				return;
			}
			// ★ 그룹화 제거 → 바로 1레벨로 붙인다
			for (AppEntry app : entries) {
				javafx.scene.control.MenuItem item =
				new javafx.scene.control.MenuItem(app.name);
				item.setGraphic(createIconView(app));
				item.setOnAction(e -> openEntry(app));
				windowsAppsMenu.getItems().add(item);
			}
		}
		private java.util.List<AppEntry> collectWindowsApps() {
			String programData = System.getenv("PROGRAMDATA");
			if (programData == null || programData.isBlank())
			programData = "C:\\ProgramData";
			String userHome = System.getProperty("user.home");
			String programs = programData + "\\Microsoft\\Windows\\Start Menu\\Programs";
			// ── 수집 Folder 목록 ──────────────────────────────────────
			String[] subDirs = {
				"Administrative Tools",  // Windows 10
				"Windows Tools",         // Windows 11 (Administrative Tools 대체)
				"Accessories",
				"System Tools",
				"Accessibility",
				"Windows PowerShell",
				"Windows Kits",
				"Maintenance",
			};
			java.util.List<File> lnkFiles = new java.util.ArrayList<>();
			for (String sub : subDirs) {
				java.nio.file.Path p =
				java.nio.file.Paths.get(programs, sub);
				lnkFiles.addAll(listLnkFilesShallow(p));
				lnkFiles.addAll(listLnkFilesRecursive(p));
			}
			// Enable자별 Administrative Tools / Windows Tools
			if (userHome != null && !userHome.isBlank()) {
				String userPrograms = userHome
				+ "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs";
				for (String sub : new String[]{"Administrative Tools","Windows Tools"}) {
					java.nio.file.Path p =
					java.nio.file.Paths.get(userPrograms, sub);
					lnkFiles.addAll(listLnkFilesShallow(p));
					lnkFiles.addAll(listLnkFilesRecursive(p));
				}
			}
			// 정렬 + 중복 제거
			lnkFiles.sort(java.util.Comparator.comparing(
				f -> f.getName().toLowerCase(java.util.Locale.ROOT),
			java.lang.String.CASE_INSENSITIVE_ORDER));
			java.util.Map<String, File> dedup = new java.util.LinkedHashMap<>();
			for (File f : lnkFiles) {
				String key = f.getName().toLowerCase(java.util.Locale.ROOT);
				dedup.putIfAbsent(key, f);
			}
			// AppEntry 생성: parseLnkTarget 으로 실제 exe Path 추출
			java.util.List<AppEntry> result = new java.util.ArrayList<>();
			for (File f : dedup.values()) {
				String lnkTarget = parseLnkTarget(f);
				String execTarget = (lnkTarget != null && !lnkTarget.isEmpty())
				? lnkTarget : f.getAbsolutePath();
				result.add(new AppEntry(
				toDisplayName(f), f, execTarget, "WINDOWS_APP"));
			}
			return result;
		}
		private java.util.List<File> listLnkFilesShallow(java.nio.file.Path root) {
			java.util.List<File> out = new java.util.ArrayList<>();
			if (root == null || !java.nio.file.Files.isDirectory(root)) return out;
			try (java.util.stream.Stream<java.nio.file.Path> stream =
				java.nio.file.Files.list(root)) {
				stream.filter(java.nio.file.Files::isRegularFile)
				.filter(this::isLnkFile)
				.sorted(java.util.Comparator.comparing(
					p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT),
					java.lang.String.CASE_INSENSITIVE_ORDER
				))
				.forEach(p -> out.add(p.toFile()));
			} catch (Exception ignored) {}
			return out;
		}
		private java.util.List<File> listLnkFilesRecursive(java.nio.file.Path root) {
			java.util.List<File> out = new java.util.ArrayList<>();
			if (root == null || !java.nio.file.Files.isDirectory(root)) return out;
			try {
				java.nio.file.Files.walkFileTree(
					root,
					new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
						@Override
						public java.nio.file.FileVisitResult visitFile(
							java.nio.file.Path file,
							java.nio.file.attribute.BasicFileAttributes attrs) {
							if (java.nio.file.Files.isRegularFile(file) && isLnkFile(file)) {
								out.add(file.toFile());
							}
							return java.nio.file.FileVisitResult.CONTINUE;
						}
						@Override
						public java.nio.file.FileVisitResult visitFileFailed(
							java.nio.file.Path file, java.io.IOException exc) {
							return java.nio.file.FileVisitResult.CONTINUE;
						}
					}
				);
			} catch (Exception ignored) {}
			out.sort(java.util.Comparator.comparing(
				f -> f.getName().toLowerCase(java.util.Locale.ROOT),
				java.lang.String.CASE_INSENSITIVE_ORDER
			));
			return out;
		}
		private boolean isLnkFile(java.nio.file.Path p) {
			if (p == null) return false;
			String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
			return name.endsWith(".lnk");
		}
		// ─────────────────────────────────────────────
		// [ MainWindow_Menu / Desktop / Windows Apps / 제어판 ]
		// ─────────────────────────────────────────────
		private java.util.List<AppEntry> collectControlPanelApps() {
			java.util.List<AppEntry> result = new java.util.ArrayList<>();
			String ps = String.join("; ",
				"$ErrorActionPreference='SilentlyContinue'",
				"$shell = New-Object -ComObject Shell.Application",
				"$cp = $shell.Namespace('shell:::{26EE0668-A00A-44D7-9371-BEB064C98683}')",
				"if ($cp -eq $null) { exit 0 }",
				"foreach ($item in $cp.Items()) {",
				"  $name = '' + $item.Name",
				"  $path = ''",
				"  try { $path = '' + $item.Path } catch {}",
				"  $parse = ''",
				"  try { $parse = '' + $cp.GetDetailsOf($item, 194) } catch {}",
				"  if ([string]::IsNullOrWhiteSpace($parse)) { $parse = $path }",
				"  $name = $name -replace \"`t\", ' ' -replace \"`r|`n\", ' '",
				"  $path = $path -replace \"`t\", ' ' -replace \"`r|`n\", ' '",
				"  $parse = $parse -replace \"`t\", ' ' -replace \"`r|`n\", ' '",
				"  Write-Output ($name + \"`t\" + $path + \"`t\" + $parse)",
				"}"
			);
			ProcessBuilder pb = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-ExecutionPolicy", "Bypass",
				"-Command",
				ps
			);
			pb.redirectErrorStream(true);
			try {
				Process p = pb.start();
				try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        p.getInputStream(),
					java.nio.charset.Charset.forName("MS949")))) {
					String line;
					while ((line = br.readLine()) != null) {
						line = line != null ? line.trim() : "";
						if (line.isEmpty()) continue;
						String[] parts = line.split("\t", -1);
						String name = parts.length > 0 ? safe(parts[0]) : "";
						String path = parts.length > 1 ? safe(parts[1]) : "";
						String parse = parts.length > 2 ? safe(parts[2]) : "";
						if (name.isEmpty()) continue;
						String execTarget = !parse.isEmpty() ? parse : path;
						if (execTarget.isEmpty()) execTarget = name;
						File f = null;
						if (!path.isEmpty()) {
							try {
								File maybe = new File(path);
								if (maybe.exists()) f = maybe;
							} catch (Exception ignored) {}
						}
						result.add(new AppEntry(name, f, execTarget, "CONTROL_PANEL"));
					}
				}
				try { p.waitFor(); } catch (InterruptedException ignored) {}
			} catch (Exception ignored) {}
			result.sort(java.util.Comparator.comparing(
				a -> a.name.toLowerCase(java.util.Locale.ROOT),
				java.lang.String.CASE_INSENSITIVE_ORDER
			));
			return result;
		}
		// ─────────────────────────────────────────────
		// 공통 그룹핑
		// ─────────────────────────────────────────────
		private java.util.Map<String, java.util.List<AppEntry>> groupByAlphabet(
            java.util.List<AppEntry> entries) {
			java.util.List<String> order = java.util.Arrays.asList(
				"0-9",
				"A-C", "D-F", "G-I", "J-L", "M-O", "P-R", "S-U", "V-Z",
				"A-G", "H-M", "N-R", "S-U", "V-Z", "Other",
				"Other"
			);
			java.util.Map<String, java.util.List<AppEntry>> grouped =
            new java.util.TreeMap<>((a, b) -> {
                int ia = order.indexOf(a);
                int ib = order.indexOf(b);
                if (ia >= 0 && ib >= 0) return java.lang.Integer.compare(ia, ib);
                if (ia >= 0) return -1;
                if (ib >= 0) return 1;
                return a.compareToIgnoreCase(b);
			});
			for (AppEntry app : entries) {
				String group = getLetterGroup(app.name);
				grouped.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(app);
			}
			for (java.util.List<AppEntry> list : grouped.values()) {
				list.sort(java.util.Comparator.comparing(
					a -> a.name.toLowerCase(java.util.Locale.ROOT),
					java.lang.String.CASE_INSENSITIVE_ORDER
				));
			}
			return grouped;
		}
		private String getLetterGroup(String name) {
			if (name == null || name.isBlank()) return "Other";
			char ch = java.lang.Character.toUpperCase(name.charAt(0));
			if (ch >= '0' && ch <= '9') return "0-9";
			if (ch >= 'A' && ch <= 'C') return "A-C";
			if (ch >= 'D' && ch <= 'F') return "D-F";
			if (ch >= 'G' && ch <= 'I') return "G-I";
			if (ch >= 'J' && ch <= 'L') return "J-L";
			if (ch >= 'M' && ch <= 'O') return "M-O";
			if (ch >= 'P' && ch <= 'R') return "P-R";
			if (ch >= 'S' && ch <= 'U') return "S-U";
			if (ch >= 'V' && ch <= 'Z') return "V-Z";
			if (ch >= '가' && ch < '라') return "A-G";
			if (ch < '바') return "H-M";
			if (ch < '아') return "N-R";
			if (ch < '차') return "S-U";
			if (ch < '\uD30C') return "V-Z";
			if (ch <= '힣') return "Other";
			return "Other";
		}
		// ─────────────────────────────────────────────
		// 실행
		// ─────────────────────────────────────────────
		private void openEntry(AppEntry app) {
			if (app == null) return;
			try {
				if (app.file != null && app.file.exists()) {
					new ProcessBuilder(
						"cmd", "/c", "start", "", app.file.getAbsolutePath()
					).start();
					return;
				}
				if (!app.execTarget.isEmpty()) {
					new ProcessBuilder(
						"explorer.exe", app.execTarget
					).start();
				}
			} catch (Exception ignored) {}
		}
		// ─────────────────────────────────────────────
		// 아이콘
		// ─────────────────────────────────────────────
		private javafx.scene.image.ImageView createIconView(AppEntry app) {
			javafx.scene.image.Image img = getIconImage(app);
			if (img == null) return null;
			javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
			iv.setFitWidth(ICON_SIZE);
			iv.setFitHeight(ICON_SIZE);
			iv.setPreserveRatio(true);
			iv.setSmooth(true);
			return iv;
		}
		private javafx.scene.image.Image getIconImageLarge(AppEntry app) {
			if (app == null || app.file == null || !app.file.exists()) return null;
			String key = app.cacheKey();
			javafx.scene.image.Image cached = iconCache.get(key);
			if (cached != null) return cached;
			try {
				if (!iconPngCacheDir.exists()) {
					iconPngCacheDir.mkdirs();
				}
				String safeName = Integer.toHexString(app.file.getAbsolutePath().toLowerCase(java.util.Locale.ROOT).hashCode());
				java.io.File pngFile = new java.io.File(iconPngCacheDir, safeName + ".png");
				if (!pngFile.exists() || pngFile.length() <= 0) {
					exportLargeIconPng(app.file, pngFile);
				}
				if (pngFile.exists() && pngFile.length() > 0) {
					javafx.scene.image.Image img = new javafx.scene.image.Image(
						pngFile.toURI().toString(),
						false
					);
					if (img != null && !img.isError()) {
						iconCache.put(key, img);
						return img;
					}
				}
			} catch (Exception ignored) {}
			// 마지막 폴백
			try {
				javax.swing.Icon icon = javax.swing.filechooser.FileSystemView
				.getFileSystemView().getSystemIcon(app.file);
				if (icon == null) return null;
				int w = Math.max(32, icon.getIconWidth());
				int h = Math.max(32, icon.getIconHeight());
				java.awt.image.BufferedImage bi =
				new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g = bi.createGraphics();
				try {
					icon.paintIcon(new javax.swing.JLabel(), g, 0, 0);
					} finally {
					g.dispose();
				}
				javafx.scene.image.Image fxImage =
				javafx.embed.swing.SwingFXUtils.toFXImage(bi, null);
				if (fxImage != null) {
					iconCache.put(key, fxImage);
					return fxImage;
				}
			} catch (Exception ignored) {}
			return null;
		}
		private javafx.scene.image.Image getIconImage(AppEntry app) {
			if (app == null) return null;
			String key = app.cacheKey();
			javafx.scene.image.Image cached = iconCache.get(key);
			if (cached != null) return cached;
			if (app.file != null && app.file.exists()) {
				try {
					Class<?> sfClass = Class.forName("sun.awt.shell.ShellFolder");
					java.lang.reflect.Method getShellFolder =
                    sfClass.getMethod("getShellFolder", File.class);
					Object sf = getShellFolder.invoke(null, app.file);
					java.lang.reflect.Method getIcon =
                    sfClass.getMethod("getIcon", boolean.class);
					java.awt.Image awtImg = (java.awt.Image) getIcon.invoke(sf, true);
					if (awtImg == null) throw new Exception("no shell icon");
					javax.swing.JLabel dummy = new javax.swing.JLabel();
					java.awt.MediaTracker tracker = new java.awt.MediaTracker(dummy);
					tracker.addImage(awtImg, 0);
					try { tracker.waitForID(0); } catch (InterruptedException ignored) {}
					int w = awtImg.getWidth(null);
					int h = awtImg.getHeight(null);
					if (w <= 0 || h <= 0) throw new Exception("invalid icon size");
					java.awt.image.BufferedImage bi =
                    new java.awt.image.BufferedImage(
                        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB
					);
					java.awt.Graphics2D g = bi.createGraphics();
					try {
						g.drawImage(awtImg, 0, 0, null);
						} finally {
						g.dispose();
					}
					javafx.scene.image.Image fxImage =
                    javafx.embed.swing.SwingFXUtils.toFXImage(bi, null);
					if (fxImage != null) iconCache.put(key, fxImage);
					return fxImage;
					} catch (Exception e1) {
					try {
						javax.swing.Icon icon = javax.swing.filechooser.FileSystemView
                        .getFileSystemView().getSystemIcon(app.file);
						if (icon == null) return null;
						int w = Math.max(ICON_SIZE, icon.getIconWidth());
						int h = Math.max(ICON_SIZE, icon.getIconHeight());
						java.awt.image.BufferedImage bi =
                        new java.awt.image.BufferedImage(
                            w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB
						);
						java.awt.Graphics2D g = bi.createGraphics();
						try {
							icon.paintIcon(new javax.swing.JLabel(), g, 0, 0);
							} finally {
							g.dispose();
						}
						javafx.scene.image.Image fxImage =
                        javafx.embed.swing.SwingFXUtils.toFXImage(bi, null);
						if (fxImage != null) iconCache.put(key, fxImage);
						return fxImage;
						} catch (Exception ignored) {
						return null;
					}
				}
			}
			return null;
		}
		private String toDisplayName(File f) {
			String name = f.getName();
			int dot = name.lastIndexOf('.');
			return (dot > 0) ? name.substring(0, dot) : name;
		}
		private String safe(String s) {
			return s == null ? "" : s.trim();
		}
		// ── .lnk 바이너리 File싱 → 타겟 full path ──────────────────────
		// MS-SHLLINK: LinkInfo(LocalBasePath) → StringData(IconLocation/RelativePath/WorkingDir)
		private String parseLnkTarget(java.io.File lnkFile) {
			if (lnkFile == null || !lnkFile.exists()) return "";
			try {
				byte[] d = java.nio.file.Files.readAllBytes(lnkFile.toPath());
				if (d.length < 76) return "";
				if (d[0] != 0x4C || d[1] != 0 || d[2] != 0 || d[3] != 0) return "";
				// LinkFlags @ 0x14
				int lf        = u32le(d, 0x14);
				boolean hasIDList   = (lf & 0x001) != 0;
				boolean hasLinkInfo = (lf & 0x002) != 0;
				boolean hasName     = (lf & 0x004) != 0;
				boolean hasRelPath  = (lf & 0x008) != 0;
				boolean hasWorkDir  = (lf & 0x010) != 0;
				boolean hasArgs     = (lf & 0x020) != 0;
				boolean hasIcon     = (lf & 0x040) != 0;
				boolean isUnicode   = (lf & 0x080) != 0;
				int pos = 76;
				// ① IDList 건너뜀
				if (hasIDList) {
					if (pos + 2 > d.length) return "";
					pos += 2 + u16le(d, pos);
				}
				// ② LinkInfo → LocalBasePath 시도 (성공 h 즉h 반환)
				if (hasLinkInfo && pos + 28 <= d.length) {
					int liStart   = pos;
					int liSize    = u32le(d, liStart);       // 구조체 All Size
					int liHdrSize = u32le(d, liStart + 4);
					int liFlags   = u32le(d, liStart + 8);
					if ((liFlags & 0x01) != 0) {            // VolumeIDAndLocalBasePath
						// Unicode (liHdrSize>=0x24, offset at liStart+28)
						if (liHdrSize >= 0x24 && liStart + 32 <= d.length) {
							int uniOff = u32le(d, liStart + 28);
							if (uniOff > 0 && liStart + uniOff < d.length) {
								String p = readUtf16le(d, liStart + uniOff);
								if (p != null && !p.isEmpty()) return expandEnvVars(p);
							}
						}
						// ANSI (offset at liStart+16)
						int ansiOff = u32le(d, liStart + 16);
						if (ansiOff > 0 && liStart + ansiOff < d.length) {
							String p = readAnsi(d, liStart + ansiOff);
							if (p != null && !p.isEmpty()) return expandEnvVars(p);
						}
					}
					// LinkInfo 건너뜀 → StringData 위치로 이동
					pos = liStart + liSize;
				}
				// ③ StringData File싱
				// 순서: NAME → RELATIVE_PATH → WORKING_DIR → ARGS → ICON_LOCATION
				String relPath = null, workDir = null, iconLoc = null;
				if (hasName)    pos = lnkSkipStr(d, pos, isUnicode);
				if (hasRelPath) { relPath = lnkReadStr(d, pos, isUnicode); pos = lnkSkipStr(d, pos, isUnicode); }
				if (hasWorkDir) { workDir = lnkReadStr(d, pos, isUnicode); pos = lnkSkipStr(d, pos, isUnicode); }
				if (hasArgs)    pos = lnkSkipStr(d, pos, isUnicode);
				if (hasIcon)    iconLoc = lnkReadStr(d, pos, isUnicode);
				// ICON_LOCATION 우선: "C:\path\app.exe,0" → 콤마 앞 부분
				if (iconLoc != null && !iconLoc.isEmpty()) {
					int comma = iconLoc.lastIndexOf(',');
					String c = expandEnvVars((comma > 0 ? iconLoc.substring(0, comma) : iconLoc).trim());
					if (!c.isEmpty() && new java.io.File(c).exists()) return c;
					if (!c.isEmpty()) return c; // File 없어도 Path 반환
				}
				// RELATIVE_PATH: lnk 부모 기준 해석
				if (relPath != null && !relPath.isEmpty()) {
					try {
						java.io.File resolved = new java.io.File(
						lnkFile.getParentFile(), relPath).getCanonicalFile();
						if (resolved.exists()) return resolved.getAbsolutePath();
					} catch (Exception ignored2) {}
				}
				// WORKING_DIR + lnk 이름.exe
				if (workDir != null && !workDir.isEmpty()) {
					workDir = expandEnvVars(workDir);
					String exeName = toDisplayName(lnkFile) + ".exe";
					java.io.File candidate = new java.io.File(workDir, exeName);
					if (candidate.exists()) return candidate.getAbsolutePath();
					return workDir; // 최소한 디렉Sat리라도 반환
				}
			} catch (Exception ignored) {}
			return "";
		}
		/** 환경변Wed 확장: %SystemRoot% → C:\Windows 등 */
		private String expandEnvVars(String s) {
			if (s == null || !s.contains("%")) return s == null ? "" : s;
			StringBuilder sb = new StringBuilder();
			int i = 0;
			while (i < s.length()) {
				int start = s.indexOf('%', i);
				if (start < 0) { sb.append(s.substring(i)); break; }
				int end = s.indexOf('%', start + 1);
				if (end < 0) { sb.append(s.substring(i)); break; }
				sb.append(s, i, start);
				String varName = s.substring(start + 1, end);
				String val = System.getenv(varName);
				sb.append(val != null ? val : s, val != null ? 0 : start, val != null ? val.length() : end + 1);
				i = end + 1;
			}
			return sb.toString();
		}
		/** StringData CountedString 읽기 */
		private String lnkReadStr(byte[] d, int pos, boolean unicode) {
			if (pos + 2 > d.length) return "";
			int count = u16le(d, pos);
			int off = pos + 2;
			if (unicode) {
				if (off + count * 2 > d.length) return "";
				try { return new String(d, off, count * 2, "UTF-16LE"); } catch (Exception e) { return ""; }
				} else {
				if (off + count > d.length) return "";
				try { return new String(d, off, count, "MS949"); } catch (Exception e) { return new String(d, off, count); }
			}
		}
		/** StringData CountedString 건너뜀 → 다음 위치 반환 */
		private int lnkSkipStr(byte[] d, int pos, boolean unicode) {
			if (pos + 2 > d.length) return pos;
			int count = u16le(d, pos);
			return pos + 2 + (unicode ? count * 2 : count);
		}
		private int u16le(byte[] d, int off) {
			return (d[off] & 0xFF) | ((d[off+1] & 0xFF) << 8);
		}
		private int u32le(byte[] d, int off) {
			return (d[off] & 0xFF) | ((d[off+1] & 0xFF) << 8)
			| ((d[off+2] & 0xFF) << 16) | ((d[off+3] & 0xFF) << 24);
		}
		private String readAnsi(byte[] d, int off) {
			if (off < 0 || off >= d.length) return "";
			int end = off;
			while (end < d.length && d[end] != 0) end++;
			try { return new String(d, off, end - off, "MS949"); }
			catch (Exception e) { return new String(d, off, end - off); }
		}
		private String readUtf16le(byte[] d, int off) {
			if (off < 0 || off + 1 >= d.length) return "";
			int end = off;
			while (end + 1 < d.length && (d[end] != 0 || d[end+1] != 0)) end += 2;
			try { return new String(d, off, end - off, "UTF-16LE"); }
			catch (Exception e) { return ""; }
		}
		public void clearIconCache() {
			iconCache.clear();
		}
		// ─────────────────────────────────────────────
		// 서브 창: General Apps / 시스템 앱 탭 2개
		// ─────────────────────────────────────────────
		private javafx.stage.Stage appWindow = null;
		public void showWindow(javafx.stage.Stage owner) {
			if (appWindow != null && appWindow.isShowing()) {
				appWindow.toFront();
				return;
			}
			javafx.scene.control.TabPane tabPane =
			new javafx.scene.control.TabPane();
			tabPane.setTabClosingPolicy(
			javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);
			javafx.scene.control.Tab tabGeneral =
			new javafx.scene.control.Tab("📦 General");
			tabGeneral.setContent(buildButtonPane(collectGeneralApps()));
			javafx.scene.control.Tab tabSystem =
			new javafx.scene.control.Tab("⚙ System");
			tabSystem.setContent(buildButtonPane(collectWindowsApps()));
			tabPane.getTabs().addAll(tabGeneral, tabSystem);
			appWindow = new javafx.stage.Stage();
			if (owner != null) appWindow.initOwner(owner);
			appWindow.setTitle("App Shortcuts");
			appWindow.setScene(new javafx.scene.Scene(tabPane, 720, 520));
			appWindow.show();
		}
		public javafx.scene.layout.Region buildGeneralPane() {
			return buildButtonPane(collectGeneralApps());
		}
		public javafx.scene.layout.Region buildSystemPane() {
			return buildButtonPane(collectWindowsApps());
		}
		private javafx.scene.layout.Region buildButtonPane(
			java.util.List<AppEntry> entries) {
			javafx.scene.layout.FlowPane flow =
			new javafx.scene.layout.FlowPane(12, 12);
			flow.setPadding(new javafx.geometry.Insets(16));
			// ── 1패스: 셀 생성 ────────────────────────────────────
			java.util.List<javafx.scene.layout.VBox> cells =
			new java.util.ArrayList<>();
			for (AppEntry app : entries) {
				// ── 아이콘 ─────────────────────────────────────────
				javafx.scene.image.ImageView iv = createIconView(app);
				if (iv != null) {
					iv.setFitWidth(36);
					iv.setFitHeight(36);
				}
				// ── 원형 배경 + DropShadow ─────────────────────────
				javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(26);
				circle.setFill(javafx.scene.paint.Color.web("rgba(255,214,235,0.60)"));
				circle.setStroke(javafx.scene.paint.Color.web("rgba(209,79,146,0.30)"));
				circle.setStrokeWidth(1.5);
				javafx.scene.effect.DropShadow shadow =
				new javafx.scene.effect.DropShadow(
				8, 0, 2, javafx.scene.paint.Color.web("rgba(209,79,146,0.35)"));
				if (iv != null) iv.setEffect(shadow);
				javafx.scene.layout.StackPane iconWrap =
				new javafx.scene.layout.StackPane(
					circle, iv != null ? iv
				: new javafx.scene.text.Text("?"));
				iconWrap.setPrefSize(56, 56);
				// ── Text ─────────────────────────────────────────
				javafx.scene.text.Text label =
				new javafx.scene.text.Text(app.name);
				label.setStyle(
					"-fx-font-family: 'Malgun Gothic';" +
				"-fx-font-size: 11px;");
				label.setFill(javafx.scene.paint.Color.web("#6b2148"));
				label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
				label.setWrappingWidth(96);
				// ── 셀 VBox ────────────────────────────────────────
				javafx.scene.layout.VBox cell =
				new javafx.scene.layout.VBox(6, iconWrap, label);
				cell.setAlignment(javafx.geometry.Pos.CENTER);
				cell.setPrefSize(100, 90);
				cell.setPadding(new javafx.geometry.Insets(6));
				cell.setFocusTraversable(true);
				cell.setStyle("-fx-background-color: transparent; -fx-background-radius: 10;");
				cell.setCursor(javafx.scene.Cursor.HAND);
				// ── Scale 애니메이션 ───────────────────────────────
				javafx.animation.ScaleTransition scaleUp =
				new javafx.animation.ScaleTransition(
				javafx.util.Duration.millis(140), iconWrap);
				scaleUp.setToX(1.22); scaleUp.setToY(1.22);
				javafx.animation.ScaleTransition scaleDown =
				new javafx.animation.ScaleTransition(
				javafx.util.Duration.millis(140), iconWrap);
				scaleDown.setToX(1.0); scaleDown.setToY(1.0);
				// ── Glow 효과 ──────────────────────────────────────
				javafx.scene.effect.Glow glow =
				new javafx.scene.effect.Glow(0.65);
				// ── 툴팁: 타겟 full path ────────────────────────────
				String tipText = !app.execTarget.isEmpty()
				? app.execTarget : app.name;
				javafx.scene.control.Tooltip tooltip =
				new javafx.scene.control.Tooltip(tipText);
				tooltip.setStyle(
					"-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;" +
					"-fx-background-color: rgba(255,245,251,0.97);" +
					"-fx-text-fill: #6b2148;" +
					"-fx-background-radius: 8;" +
					"-fx-border-color: rgba(209,79,146,0.45);" +
					"-fx-border-radius: 8;" +
					"-fx-border-width: 1;" +
				"-fx-padding: 6 10 6 10;");
				tooltip.setShowDelay(javafx.util.Duration.ZERO);
				javafx.scene.control.Tooltip.install(cell, tooltip);
				// ── hover ──────────────────────────────────────────
				cell.setOnMouseEntered(e -> {
					cell.setStyle(
						"-fx-background-color: rgba(209,79,146,0.13);" +
					"-fx-background-radius: 10;");
					scaleDown.stop(); scaleUp.play();
					if (iv != null) iv.setEffect(glow);
					circle.setFill(
					javafx.scene.paint.Color.web("rgba(255,180,220,0.80)"));
				});
				cell.setOnMouseExited(e -> {
					cell.setStyle(
						"-fx-background-color: transparent;" +
					"-fx-background-radius: 10;");
					scaleUp.stop(); scaleDown.play();
					if (iv != null) iv.setEffect(shadow);
					circle.setFill(
					javafx.scene.paint.Color.web("rgba(255,214,235,0.60)"));
				});
				// ── 포커스 테두리 + 키보드 포커스 h 툴팁 표h ────
				cell.focusedProperty().addListener((o, old, focused) -> {
					if (focused) {
						cell.setStyle(
							"-fx-background-color: rgba(209,79,146,0.13);" +
							"-fx-background-radius: 10;" +
							"-fx-border-color: rgba(209,79,146,0.55);" +
							"-fx-border-radius: 10;" +
						"-fx-border-width: 1.5;");
						// 마우스가 없어도 툴팁 표시
						javafx.application.Platform.runLater(() -> {
							if (!cell.isFocused()) return;
							javafx.geometry.Bounds b =
							cell.localToScreen(cell.getBoundsInLocal());
							if (b != null)
							tooltip.show(cell,
								b.getMinX(),
							b.getMaxY() + 4);
						});
						} else {
						cell.setStyle(
							"-fx-background-color: transparent;" +
						"-fx-background-radius: 10;");
						tooltip.hide();
					}
				});
				// ── 클릭: 1회=포커스, 2회=실행, 우클릭=팝업 ──────
				cell.setOnMouseClicked(e -> {
					if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
						// 우클릭 → Favorites 팝업
						javafx.scene.control.ContextMenu cm =
						new javafx.scene.control.ContextMenu();
						javafx.scene.control.MenuItem miFav =
						new javafx.scene.control.MenuItem("⭐ Add to Favorites");
						javafx.scene.control.MenuItem miCancel =
						new javafx.scene.control.MenuItem("✖ Cancel");
						miFav.setOnAction(ae -> {
							if (favoriteCallback != null) {
								String favName = app.name;
								String favPath = !app.execTarget.isEmpty()
								? app.execTarget : (app.file != null
								? app.file.getAbsolutePath() : "");
								if (!favPath.isEmpty())
								favoriteCallback.addFavorite(favName, favPath);
							}
						});
						cm.getItems().addAll(miFav, miCancel);
						cm.show(cell, e.getScreenX(), e.getScreenY());
						return;
					}
					cell.requestFocus();
					if (e.getClickCount() == 2) openEntry(app);
				});
				cells.add(cell);
				flow.getChildren().add(cell);
			}
			// ── 2패스: 셀별 엔터/스페이스만 처리 ───────────────
			for (int i = 0; i < cells.size(); i++) {
				final int idx = i;
				cells.get(i).setOnKeyPressed(e -> {
					javafx.scene.input.KeyCode code = e.getCode();
					if (code == javafx.scene.input.KeyCode.ENTER ||
						code == javafx.scene.input.KeyCode.SPACE) {
						openEntry(entries.get(idx));
						e.consume();
					}
				});
			}
			javafx.scene.control.ScrollPane scroll =
			new javafx.scene.control.ScrollPane(flow);
			scroll.setFitToWidth(true);
			scroll.setFocusTraversable(false);
			scroll.setStyle(
				"-fx-background-color: transparent;" +
			"-fx-background: transparent;");
			// ── ScrollPane EventFilter: 화살표=네비게이션, 나머지=통과 ──
			// (Filter 는 위→아래 캡처 단계에서 실행되므로
			//  여기서 처리 후 consume 해야 ScrollPane 스크롤을 막을 수 있음)
			final java.util.List<javafx.scene.layout.VBox> cellsRef = cells;
			scroll.addEventFilter(
				javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
					javafx.scene.input.KeyCode kc = e.getCode();
					if (kc != javafx.scene.input.KeyCode.UP   &&
						kc != javafx.scene.input.KeyCode.DOWN &&
						kc != javafx.scene.input.KeyCode.LEFT &&
					kc != javafx.scene.input.KeyCode.RIGHT) return;
					// 현재 포커스된 셀 찾기
					javafx.scene.Node focused = scroll.getScene() == null ? null
					: scroll.getScene().getFocusOwner();
					int curIdx = -1;
					for (int i = 0; i < cellsRef.size(); i++) {
						if (cellsRef.get(i) == focused) { curIdx = i; break; }
					}
					if (curIdx < 0) { e.consume(); return; }
					javafx.scene.layout.VBox cur = cellsRef.get(curIdx);
					javafx.geometry.Bounds b = cur.getBoundsInParent();
					double cx = b.getCenterX(), cy = b.getCenterY();
					int best = -1; double bestDist = Double.MAX_VALUE;
					for (int j = 0; j < cellsRef.size(); j++) {
						if (j == curIdx) continue;
						javafx.geometry.Bounds ob = cellsRef.get(j).getBoundsInParent();
						double ox = ob.getCenterX(), oy = ob.getCenterY();
						boolean match = false;
						switch (kc) {
							case LEFT:  match = ox < cx-10 && Math.abs(oy-cy) < ob.getHeight(); break;
							case RIGHT: match = ox > cx+10 && Math.abs(oy-cy) < ob.getHeight(); break;
							case UP:    match = oy < cy-10 && Math.abs(ox-cx) < ob.getWidth();  break;
							case DOWN:  match = oy > cy+10 && Math.abs(ox-cx) < ob.getWidth();  break;
							default: break;
						}
						if (!match) continue;
						double dist = Math.hypot(ox-cx, oy-cy);
						if (dist < bestDist) { bestDist = dist; best = j; }
					}
					if (best >= 0) {
						javafx.scene.layout.VBox target = cellsRef.get(best);
						target.requestFocus();
						// ── 셀이 뷰포트 밖이면 스크롤 이동 ──────────────
						javafx.application.Platform.runLater(() -> {
							javafx.geometry.Bounds cellB = target.getBoundsInParent();
							javafx.geometry.Bounds viewB = scroll.getViewportBounds();
							double flowH = flow.getBoundsInLocal().getHeight();
							double excess = flowH - viewB.getHeight();
							if (excess <= 0) return;
							double curV = scroll.getVvalue();
							double topVisible    = curV * excess;
							double bottomVisible = topVisible + viewB.getHeight();
							if (cellB.getMinY() < topVisible) {
								// 위로 벗어남 → 셀 상단이 뷰포트 상단에 오도록
								scroll.setVvalue(cellB.getMinY() / excess);
								} else if (cellB.getMaxY() > bottomVisible) {
								// 아래로 벗어남 → 셀 Other단이 뷰포트 Other단에 오도록
								scroll.setVvalue((cellB.getMaxY() - viewB.getHeight()) / excess);
							}
						});
					}
					e.consume(); // ScrollPane Scroll 방지
				});
				return scroll;
		}
	}
	private static String psQuote(String s) {
		if (s == null) return "''";
		return "'" + s.replace("'", "''") + "'";
	}
	public static void exportLargeIconPng(java.io.File sourceFile, java.io.File outPng) {
		if (sourceFile == null || !sourceFile.exists() || outPng == null) return;
		String src = psQuote(sourceFile.getAbsolutePath());
		String dst = psQuote(outPng.getAbsolutePath());
		String script =
        "$src = " + src + "; " +
        "$dst = " + dst + "; " +
        "Add-Type -AssemblyName System.Drawing; " +
        "Add-Type -AssemblyName System.Windows.Forms; " +
        "$code = @'\n" +
        "using System;\n" +
        "using System.Drawing;\n" +
        "using System.Runtime.InteropServices;\n" +
        "public class IconExport {\n" +
        "  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Auto)]\n" +
        "  public struct SHFILEINFO {\n" +
        "    public IntPtr hIcon;\n" +
        "    public int iIcon;\n" +
        "    public uint dwAttributes;\n" +
        "    [MarshalAs(UnmanagedType.ByValTStr, SizeConst=260)]\n" +
        "    public string szDisplayName;\n" +
        "    [MarshalAs(UnmanagedType.ByValTStr, SizeConst=80)]\n" +
        "    public string szTypeName;\n" +
        "  }\n" +
        "  [DllImport(\"shell32.dll\", CharSet=CharSet.Auto)]\n" +
        "  public static extern IntPtr SHGetFileInfo(string pszPath, uint dwFileAttributes, out SHFILEINFO psfi, uint cbFileInfo, uint uFlags);\n" +
        "  [DllImport(\"user32.dll\", SetLastError=true)]\n" +
        "  public static extern bool DestroyIcon(IntPtr hIcon);\n" +
        "}\n" +
        "'@; " +
        "Add-Type $code; " +
        "$SHGFI_ICON = 0x100; " +
        "$SHGFI_LARGEICON = 0x0; " +
        "$info = New-Object IconExport+SHFILEINFO; " +
        "[void][IconExport]::SHGetFileInfo($src, 0, [ref]$info, [uint32][Runtime.InteropServices.Marshal]::SizeOf($info), $SHGFI_ICON -bor $SHGFI_LARGEICON); " +
        "if ($info.hIcon -ne [IntPtr]::Zero) { " +
        "  $icon = [System.Drawing.Icon]::FromHandle($info.hIcon); " +
        "  $bmp = $icon.ToBitmap(); " +
        "  $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png); " +
        "  $bmp.Dispose(); " +
        "  $icon.Dispose(); " +
        "  [void][IconExport]::DestroyIcon($info.hIcon); " +
        "}";
		try {
			ProcessBuilder pb = new ProcessBuilder(
				"powershell.exe",
				"-NoProfile",
				"-ExecutionPolicy", "Bypass",
				"-Command",
				script
			);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(),
				java.nio.charset.Charset.forName("MS949")))) {
				while (br.readLine() != null) {
					// 버림
				}
			}
			p.waitFor();
		} catch (Exception ignored) {}
	}
	
	// ── 추가 import (File 상단에 합산) ──────────────────────────────────────
	// import javafx.animation.PauseTransition;
	// import javafx.application.Platform;
	// import javafx.scene.control.*;
	// import javafx.scene.layout.*;
	// import javafx.stage.DirectoryChooser;
	// import javafx.stage.Stage;
	// import javafx.util.Duration;
	// ────────────────────────────────────────────────────────────────────────
	
	public static void doUpgrade() {
		// ── 설치 Folder 기본값 계산 ────────────────────────────────────────────
		java.io.File exeFile0 = new java.io.File(
			AppContext.theExePath.isEmpty()
            ? AppLogger.getExeFilePath()
            : AppContext.theExePath
		).getAbsoluteFile();
		
		java.io.File kootDir0         = exeFile0.getParentFile() != null
		? exeFile0.getParentFile()
		: new java.io.File(".");
		java.io.File defaultInstallDir = kootDir0.getParentFile() != null
		? kootDir0.getParentFile()
		: kootDir0;
		
		System.out.println("설치 Folder : " + defaultInstallDir.getAbsoluteFile());
		
		// ── 확인 다이얼로그 (FX) ─────────────────────────────────────────────
		final java.io.File[] chosenDir = { defaultInstallDir };
		
		// 메시지
		Label msgLabel = new Label(
			"Download DownLoad_UpGrade.zip from GitHub into the folder below\n"
			+ "extract it, then the Upgrade will begin.\n\n"
			+ "When done, this program will shut down automatically.\n\n"
			+ "Do you want to continue?"
		);
		msgLabel.setWrapText(true);
		
		// Folder Path 표시
		TextField dirField = new TextField(chosenDir[0].getAbsolutePath());
		dirField.setEditable(false);
		dirField.setPrefWidth(340);
		
		// Folder Select 버튼
		Button browseBtn = new Button("Folder Select...");
		browseBtn.setOnAction(e -> {
			DirectoryChooser dc = new DirectoryChooser();
			dc.setTitle("Select Install Folder");
			dc.setInitialDirectory(chosenDir[0].exists() ? chosenDir[0] : null);
			// ownerStage: FxSplashWindow 가 보유한 Stage 필드를 전달
			java.io.File selected = dc.showDialog(ownerStage);
			if (selected != null) {
				chosenDir[0] = selected;
				dirField.setText(selected.getAbsolutePath());
			}
		});
		
		// 레이아웃
		HBox dirRow = new HBox(6, new Label("Install Folder:"), dirField, browseBtn);
		dirRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		
		VBox content = new VBox(10, msgLabel, dirRow);
		content.setPadding(new javafx.geometry.Insets(4, 0, 0, 0));
		
		// 확인 다이얼로그
		Dialog<ButtonType> dlg = new Dialog<>();
		dlg.setTitle("Program Upgrade");
		dlg.setHeaderText(null);
		dlg.getDialogPane().setContent(content);
		dlg.getDialogPane().getButtonTypes()
		.addAll(ButtonType.YES, ButtonType.NO);
		dlg.initOwner(ownerStage);
		
		java.util.Optional<ButtonType> answer = dlg.showAndWait();
		if (!answer.isPresent() || answer.get() != ButtonType.YES) return;
		
		final java.io.File selectedInstallDir = chosenDir[0];
		
		// ── 업그레이드 백그라운드 스레드 ────────────────────────────────────
		new Thread(() -> {
			try {
				// 0. 시작프로그램 등록 해제 ─────────────────────────────────
				if (AppRestarter.AutoStart.check()) {
					boolean unregistered = AppRestarter.AutoStart.set(false);
					System.out.println(unregistered	? "✅ 시작프로그램 등록 해제 Done"
					: "⚠️  Auto-start unregister failed (ignored, continuing)");
					} else {
					System.out.println("ℹ️  시작프로그램 미등록 — 해제 생략");	
				}
				
				// 1. 설치 Folder 결정 ─────────────────────────────────────────
				java.io.File installDir = selectedInstallDir;
				System.out.println("[Upgrade] zip Save Folder: " + installDir.getAbsolutePath());
				
				// 2. DownLoad_UpGrade.zip Download ─────────────────────────
				String zipUrl = "https://github.com/GarpsuKim/KootPanKingThree/raw/refs/heads/main/3D_BAT/DownLoad_UpGrade.zip";
				java.io.File zipFile = new java.io.File(installDir, "DownLoad_UpGrade.zip");
				System.out.println("[Upgrade] Download Start: " + zipUrl);
				
				java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URI(zipUrl).toURL().openConnection();
				conn.setConnectTimeout(15000);
				conn.setReadTimeout(60000);
				try (java.io.InputStream in  = conn.getInputStream();
					java.io.FileOutputStream fos = new java.io.FileOutputStream(zipFile)) {
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
				}
				conn.disconnect();
				System.out.println("[Upgrade] Download Done: " + zipFile.getAbsolutePath()
				+ "  (" + zipFile.length() + " bytes)");
				
				// 3. zip 압축 해제 ──────────────────────────────────────────
				System.out.println("[Upgrade] 압축 해제 시작");
				try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
				new java.io.FileInputStream(zipFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    java.io.File dest = new java.io.File(installDir, entry.getName());
                    // Zip-Slip 방지
                    if (!dest.getCanonicalPath()
						.startsWith(installDir.getCanonicalPath())) {
                        System.err.println("[Upgrade] 위험한 Path 무시: " + entry.getName());
                        zis.closeEntry();
                        continue;
					}
                    if (entry.isDirectory()) {
                        dest.mkdirs();
						} else {
                        dest.getParentFile().mkdirs();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
						}
                        System.out.println("[Upgrade] 해제: " + dest.getAbsolutePath());
					}
                    zis.closeEntry();
				}
				}
				System.out.println("[Upgrade] 압축 해제 Done");
				
				// 4. 실행 File 탐색 (BAT 우선, 없으면 EXE) ─────────────────
				java.io.File[] bats = installDir.listFiles(
					f -> f.isFile() && f.getName().toLowerCase().endsWith(".bat")
				&& !f.getName().equalsIgnoreCase("DownLoad_UpGrade.zip"));
				java.io.File[] exes = installDir.listFiles(
					f -> f.isFile() && f.getName().toLowerCase().endsWith(".exe")
				&& !f.getName().equalsIgnoreCase(exeFile0.getName()));
				
				java.io.File runTarget = null;
				if (bats != null && bats.length > 0) {
					runTarget = bats[0];
					for (java.io.File f : bats)
                    if (f.getName().toLowerCase().contains("download_upgrade")) {
                        runTarget = f; break;
					}
					} else if (exes != null && exes.length > 0) {
					runTarget = exes[0];
				}
				
				if (runTarget == null) {
					throw new Exception(
						"Executable not found (bat/exe missing)\nPath: "
					+ installDir.getAbsolutePath());
				}
				System.out.println("[Upgrade] 실행 대상: " + runTarget.getAbsolutePath());
				
				// 5. 실행 ──────────────────────────────────────────────────
				ProcessBuilder pb = new ProcessBuilder(
				"cmd", "/c", "start", "\"\"", runTarget.getAbsolutePath());
				pb.directory(installDir);
				pb.start();
				
				// 6. 프로그램 종료 (FX 스레드로 위임) ─────────────────────
				Platform.runLater(() -> {
					System.out.println("🚀 업데이터 실행됨 — 프로그램을 종료합니다.");	
					PauseTransition pause = new PauseTransition(Duration.seconds(1));
					pause.setOnFinished(ev -> {
						if (exitCallback != null) exitCallback.run();
						else System.exit(0);
					});
					pause.play();
				});
				
				} catch (Exception ex) {
				System.out.println("❌ 업그레이드 오류: " + ex.getMessage());				
				
				ex.printStackTrace();
				// FX 스레드에서 오류 Alert 표시
				Platform.runLater(() -> {
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setTitle("Upgrade Error");
					alert.setHeaderText(null);
					alert.setContentText("Error during Upgrade:\n" + ex.getMessage());
					alert.initOwner(ownerStage);
					alert.showAndWait();
				});
			}
		}, "UpgradeThread").start();
	}	
}