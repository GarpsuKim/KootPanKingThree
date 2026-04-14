import java.io.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.net.URI;
import java.awt.Desktop;
import java.nio.file.*;

public final class AppContext {
	
    private AppContext() {}
	
    // =========================================================
    // [1] 메타
    // =========================================================
	public static final String projectName = "KootPanKingThree";
    public static final String APP_NAME = projectName;
    // =========================================================
    // [2] config
    // =========================================================
    public static final String workingDir = System.getProperty("user.dir");
    private static Properties CONFIG = new Properties();
	// private static IniController iniController ;	
	
    // ── 설정 파일 저장 폴더 결정 (우선순위 3단계) ─────────────────
	public static String APP_DIR ;	
    public static String theExePath;
	public static File theExeFile;
    public static String SETTINGS_DIR;
    // ── 인스턴스별 설정 파일 경로 및 자식 여부 ─────────────────────
    // 기본 인스턴스 : clock_settings.ini  (CONFIG_FILE 과 동일)
    // 자식 인스턴스 : clock_settings_<CityName>.ini
    // String myConfigFile = CONFIG_FILE; // 기본값: 부모와 동일
	public static String myConfigFile;
	
    public static String CONFIG_FILE = "clock_settings.ini";
    // ★ GitHub raw URL
    private static final String MASTER_DEFAULT_CONFIG_URL =  "https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/INI_bak/clock_settings_default.ini";
    // =========================================================
    // [3] 경로
    // =========================================================
	
    // =========================================================
    // [4] runtime
    // =========================================================
    public static Instant startTime;
    // =========================================================
    // [5] 공유 상태
    // =========================================================
    public static volatile String status = "IDLE";
    public static volatile String currentJob = "";
    public static volatile String message = "";
    public static final AtomicInteger counter = new AtomicInteger(0);
    public static final AtomicLong lastUpdateTime = new AtomicLong(0);
    // =========================================================
    // [6] 콜백 인터페이스
    // =========================================================
    public interface StateListener {
        void onStateChanged(String newState);
	}
    private static final List<StateListener> listeners =
	new CopyOnWriteArrayList<>();
    // =========================================================
    // [7] 초기화
    // =========================================================
    public static void init() {
		
		APP_DIR = resolveAppDir();	
		String exePath = resolveExePath(projectName);
		theExePath = exePath != null ? exePath : "(unknown)";
		// 실행 파일 정보 (baseName 용도로만 사용)
		theExeFile = theExePath != null ? new File(theExePath) : null;
        SETTINGS_DIR = resolveSettingsDir(projectName);
        CONFIG_FILE = SETTINGS_DIR + CONFIG_FILE;
		
        ensureMasterConfigFile(CONFIG_FILE);   // ★ 여기 핵심
        loadConfig(CONFIG_FILE);
		
        startTime = Instant.now();
        lastUpdateTime.set(System.currentTimeMillis());
		
        System.out.println("[init] APP_DIR      : " + APP_DIR );
        System.out.println("[init] theExePath   : " + theExePath );
        System.out.println("[init] theExeFile   : " + theExeFile.getAbsolutePath() );
        System.out.println("[init] SETTINGS_DIR : " + SETTINGS_DIR );
        System.out.println("[init] CONFIG_FILE  : " + CONFIG_FILE );
	}
	
	/** %APPDATA%\KootPanKingThree\ 경로 결정 */
    private static String resolveAppDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        java.io.File dir = new java.io.File(appData + java.io.File.separator + "KootPanKingThree");
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath() + java.io.File.separator;
	}	
	
    // =========================================================
    // [8] config 파일 보장 (없으면 다운로드)
    // =========================================================
	private static void ensureMasterConfigFile(String CONFIG_FILE) {
		try {
			Path path = Path.of(CONFIG_FILE);
			Files.createDirectories(path.getParent());  // ✔ try 안으로 이동
			if (Files.exists(path)) {
				System.out.println("config.ini FOUND : " + CONFIG_FILE);
				return;
			}
			System.out.println("config.ini not found. downloading..." + CONFIG_FILE);
			try (InputStream in = URI.create(MASTER_DEFAULT_CONFIG_URL)
				.toURL()
				.openStream()) {				
				Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
			}
			/*
				try (InputStream in = new URL(MASTER_DEFAULT_CONFIG_URL).openStream()) {
				Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
				}
			*/
			System.out.println("config.ini downloaded." + CONFIG_FILE);
			} catch (Exception e) {
			System.out.println("[ERROR] download failed." + MASTER_DEFAULT_CONFIG_URL );
			try {
				Files.createFile(Path.of(CONFIG_FILE));
				System.out.println("Empty CONFIG : " + CONFIG_FILE);
				} catch (IOException ex) {
				System.out.println("[ERROR] Empty CONFIG creation failed : " + CONFIG_FILE);
				throw new RuntimeException(ex);
			}
		}
	}
	// 최초 생성 또는 복사
	public static String ensureCityConfigFile(String cityName, String clockPrefix, String zoneId) {
		try {
			String safeName = cityName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
			
			Path source = Path.of(CONFIG_FILE);
			Path target = Path.of(SETTINGS_DIR, "clock_settings_" + safeName + ".ini");
			
			Files.createDirectories(target.getParent());
			
			if (!Files.exists(target)) {
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
			
			Properties p = new Properties();
			try (FileInputStream fis = new FileInputStream(target.toFile())) {
				p.load(fis);
			}
			
			p.setProperty("cityName", clockPrefix);
			p.setProperty("timeZone", zoneId);
			
			try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
				p.store(fos, "KootPanKingThree Child Settings - " + cityName);
			}
			
			return target.toString();
			
			} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static boolean cityConfigFileExists(String cityName) {
		try {
			String safeName = cityName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
			Path target = Path.of(SETTINGS_DIR, "clock_settings_" + safeName + ".ini");
			return Files.exists(target);
			} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String copyCityConfigFile(String cityName, String clockPrefix, String zoneId) {
		try {
			String safeName = cityName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
			
			Path source = Path.of(CONFIG_FILE);
			Path target = Path.of(SETTINGS_DIR, "clock_settings_" + safeName + ".ini");
			
			Files.createDirectories(target.getParent());
			
			// 무조건 복사(덮어쓰기)
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			
			Properties p = new Properties();
			try (FileInputStream fis = new FileInputStream(target.toFile())) {
				p.load(fis);
			}
			
			p.setProperty("cityName", clockPrefix);
			p.setProperty("timeZone", zoneId);
			
			try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
				p.store(fos, "KootPanKingThree Child Settings - " + cityName);
			}
			
			return target.toString();
			
			} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}	
	
	
	// =========================================================
    // [9] 경로
    // =========================================================
	
	private static String resolveSettingsDir(String projectName) {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        return appData + File.separator + projectName + File.separator + "settings" + File.separator;
	}
	
    private static String resolveExePath(String projectName) {
        // ① sun.java.command - .jar 또는 .exe (java/javaw 제외)
        //    .jar 인 경우 옆에 KootPanKingThree.exe 가 있으면 exe 를 우선 반환
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = "";
            if (sc.startsWith("\"")) {
                int end = sc.indexOf("\"", 1);
                if (end > 1) first = sc.substring(1, end);
				} else if (!sc.isEmpty()) {
                first = sc.split("\\s+")[0];
			}
            if (first.endsWith(".exe")) {
                return new File(first).getAbsolutePath();
				} else if (first.endsWith(".jar")) {
                File jarFile = new File(first).getAbsoluteFile();
                File parent  = jarFile.getParentFile();
                if (parent != null) {
					String exeFile = projectName + ".exe";
                    File exeCandidate = new File(parent, exeFile);
                    if (exeCandidate.exists()) return exeCandidate.getAbsolutePath();
				}
                return jarFile.getAbsolutePath();
			}
		} catch (Exception ignored) {}
        // ② CodeSource (JAR / class 실행)
        try {
            java.security.CodeSource cs =
			AppContext.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                File f = new File(cs.getLocation().toURI()).getAbsoluteFile();
                String name = f.getName().toLowerCase();
                if (name.equals("java.exe") || name.equals("javaw.exe")
					|| name.equals("java")     || name.equals("javaw")) {
                    // java/javaw → 건너뜀, ProcessHandle ③ 에서 처리
					} else if (f.isDirectory()) {
                    // IDE/class 직접 실행: 디렉터리 안에 exe 있는지 탐색
					String exeFile = projectName + ".exe";
                    File exeCandidate = new File(f, exeFile);
                    if (exeCandidate.exists()) return exeCandidate.getAbsolutePath();
					} else if (name.endsWith(".jar")) {
                    // jar 옆에 exe 있으면 exe 우선
                    File parent = f.getParentFile();
                    if (parent != null) {
						String exeFile = projectName + ".exe";
                        File exeCandidate = new File(parent, exeFile);
                        if (exeCandidate.exists()) return exeCandidate.getAbsolutePath();
					}
                    return f.getAbsolutePath();
					} else {
                    return f.getAbsolutePath();
				}
			}
		} catch (Exception ignored) {}
        // ③ ProcessHandle 기반 명령행 파싱 (Java 9+) - 최후 수단
        try {
            java.util.Optional<String> cmd =
			ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                File f = new File(cmd.get());
                String name = f.getName().toLowerCase();
                // java/javaw 이면 사용하지 않음
                if (f.exists()
                    && !name.equals("java.exe") && !name.equals("javaw.exe")
                    && !name.equals("java")     && !name.equals("javaw")) {
                    return f.getAbsolutePath();
				}
			}
		} catch (Exception ignored) {}
        // ④ 현재 작업 디렉터리 기준 (최후의 최후)
        return null;
	}
    // =========================================================
    // [10] ini load
    // =========================================================
    private static void loadConfig(String CONFIG_FILE) {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            CONFIG.load(fis);
			} catch (IOException ignored) {
			System.out.println("[ERROR] NOT EXIST (config.ini)");
		}
	}
    // =========================================================
    // [11] ini read
    // =========================================================
    public static String get(String key) {
        return CONFIG.getProperty(key);
	}
    public static String get(String key, String def) {
        return CONFIG.getProperty(key, def);
	}
    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(CONFIG.getProperty(key));
			} catch (Exception e) {
            return def;
		}
	}
    public static long getLong(String key, long def) {
        try {
            return Long.parseLong(CONFIG.getProperty(key));
			} catch (Exception e) {
            return def;
		}
	}
    public static boolean getBoolean(String key, boolean def) {
        String v = CONFIG.getProperty(key);
        return (v == null) ? def : Boolean.parseBoolean(v);
	}
    // =========================================================
    // [12] ini write
    // =========================================================
    public static void set(String key, String value) {
        CONFIG.setProperty(key, value);
	}
    public static void setInt(String key, int value) {
        CONFIG.setProperty(key, String.valueOf(value));
	}
    public static void setLong(String key, long value) {
        CONFIG.setProperty(key, String.valueOf(value));
	}
    public static void setBoolean(String key, boolean value) {
        CONFIG.setProperty(key, String.valueOf(value));
	}
    public static void remove(String key) {
        CONFIG.remove(key);
	}
    // =========================================================
    // [13] save
    // =========================================================
    public static synchronized void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            CONFIG.store(fos, null);
			} catch (IOException e) {
            throw new RuntimeException("config save failed", e);
		}
	}
    // =========================================================
    // [14] 상태
    // =========================================================
    public static void setStatus(String newStatus) {
        status = newStatus;
        lastUpdateTime.set(System.currentTimeMillis());
        for (StateListener l : listeners) {
            l.onStateChanged(newStatus);
		}
	}
    public static String getStatus() {
        return status;
	}
    public static void setCurrentJob(String job) {
        currentJob = job;
        lastUpdateTime.set(System.currentTimeMillis());
	}
    public static String getCurrentJob() {
        return currentJob;
	}
    public static void setMessage(String msg) {
        message = msg;
        lastUpdateTime.set(System.currentTimeMillis());
	}
    public static String getMessage() {
        return message;
	}
    public static int incCounter() {
        lastUpdateTime.set(System.currentTimeMillis());
        return counter.incrementAndGet();
	}
    public static int decCounter() {
        lastUpdateTime.set(System.currentTimeMillis());
        return counter.decrementAndGet();
	}
    public static int getCounter() {
        return counter.get();
	}
    // =========================================================
    // [15] listener
    // =========================================================
    public static void addListener(StateListener l) {
        if (l != null) listeners.add(l);
	}
    public static void removeListener(StateListener l) {
        listeners.remove(l);
	}
	// =========================================================
    // [15] 디버그
    // =========================================================
    public static void printAll(Properties CONFIG) {
		for (String key : CONFIG.stringPropertyNames()) {
			System.out.println(key + " = [" + CONFIG.getProperty(key) + "]" );
		}
	}
    public static String stringAll(Properties CONFIG) {
		String  result = "\n";
		for (String key : CONFIG.stringPropertyNames()) {
			result = result  + key + " = [" + CONFIG.getProperty(key) + "]\n" ;
		}
		return result;
	}
	private static void openIniFile(String path) {
        try {
            if (path == null || path.trim().isEmpty()) return;
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return;
            if (java.awt.Desktop.isDesktopSupported())
			java.awt.Desktop.getDesktop().open(f);
			} catch (Exception e) {
            System.err.println("로그 파일 열기 실패: " + e.getMessage());
		}
	}
	
	// AppContext.java
	public static boolean openCONFIG_FILE() {
		return openCONFIG_FILE(CONFIG_FILE);
	}
	
	public static boolean openCONFIG_FILE(String configFilePath) {
		try {
			File f = new File(configFilePath);
			if (!f.exists()) return false;
			
			if (Desktop.isDesktopSupported()) {
				Desktop desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.OPEN)) {
					desktop.open(f);
					return true;
				}
				if (desktop.isSupported(Desktop.Action.BROWSE)) {
					desktop.browse(f.toURI());
					return true;
				}
			}
			new ProcessBuilder("notepad.exe", f.getAbsolutePath()).start();
			} catch (Exception e) {
			System.out.println("[CONFIG_FILE] " + e.getMessage());
			return false;
		}
		return true;
	}
	
	// =========================================================
    // [16] 마스터 ini 전용 — Gmail / Naver / Telegram 계정 정보
    //      모든 자식 인스턴스가 공유하며, 마스터 ini(CONFIG_FILE)에만 저장된다.
    // =========================================================

    // ── Gmail ───────────────────────────────────────────────────
    /** Gmail 발신 계정 (예: xxx@gmail.com) */
    public static String getGmailFrom() {
        return get("gmail.from", "");
    }
    public static void setGmailFrom(String from) {
        set("gmail.from", from != null ? from.trim() : "");
        save();
    }

    /** Gmail 앱 비밀번호 */
    public static String getGmailPass() {
        return get("gmail.pass", "");
    }
    public static void setGmailPass(String pass) {
        set("gmail.pass", pass != null ? pass.trim() : "");
        save();
    }

    // ── Naver CalDAV ────────────────────────────────────────────
    /** 네이버 CalDAV 아이디 */
    public static String getNaverId() {
        return get("naver.caldav.id", "");
    }
    public static void setNaverId(String id) {
        set("naver.caldav.id", id != null ? id.trim() : "");
        save();
    }

    /** 네이버 CalDAV 비밀번호 */
    public static String getNaverPassword() {
        return get("naver.caldav.password", "");
    }
    public static void setNaverPassword(String password) {
        set("naver.caldav.password", password != null ? password.trim() : "");
        save();
    }

    // ── Telegram ────────────────────────────────────────────────
    /** 텔레그램 봇 토큰 */
    public static String getTelegramBotToken() {
        return get("tg.botToken", "");
    }
    public static void setTelegramBotToken(String token) {
        set("tg.botToken", token != null ? token.trim() : "");
        save();
    }

    /** 텔레그램 내 채팅 ID */
    public static String getTelegramMyChatId() {
        return get("tg.myChatId", "");
    }
    public static void setTelegramMyChatId(String chatId) {
        set("tg.myChatId", chatId != null ? chatId.trim() : "");
        save();
    }

	// =========================================================
    // [17] 마스터 ini 전용 — 카메라 / YouTube / CCTV 공유 설정
    //      모든 자식 인스턴스가 공유하며, 마스터 ini(CONFIG_FILE)에만 저장된다.
    // =========================================================

    // ── 스마트폰 카메라 ─────────────────────────────────────────
    /** 마지막 카메라 스트림 주소 (예: http://192.168.0.70:8080) */
    public static String getCameraUrl() {
        return get("camera.url", "http://192.168.0.100:8080");
    }
    public static void setCameraUrl(String url) {
        set("camera.url", url != null ? url : "");
        save();
    }

    /** ffmpeg 실행 파일 경로 (카메라·YouTube 공용) */
    public static String getFfmpegPath() {
        return get("ffmpeg.path", "");
    }
    public static void setFfmpegPath(String path) {
        set("ffmpeg.path", path != null ? path : "");
        save();
    }

    // ── YouTube 실시간 ──────────────────────────────────────────
    /** 마지막 YouTube 스트림 URL */
    public static String getYoutubeUrl() {
        return get("youtube.url", "");
    }
    public static void setYoutubeUrl(String url) {
        set("youtube.url", url != null ? url : "");
        save();
    }

    /** yt-dlp 실행 파일 경로 */
    public static String getYtdlpPath() {
        return get("youtube.ytdlp.path", "");
    }
    public static void setYtdlpPath(String path) {
        set("youtube.ytdlp.path", path != null ? path : "");
        save();
    }

    // ── ITS 교통 CCTV ───────────────────────────────────────────
    /** ITS 교통 CCTV API 키 */
    public static String getItsCctvApiKey() {
        return get("its.cctv.apiKey", "");
    }
    public static void setItsCctvApiKey(String key) {
        set("its.cctv.apiKey", key != null ? key : "");
        save();
    }

	/*
		public static void main(String[] args) {
        AppContext.init();
        AppContext.addListener(new StateListener() {
		@Override
		public void onStateChanged(String s) {
		System.out.println("changed: " + s);
		}
		});
		printAll(CONFIG);
        AppContext.setStatus("RUNNING");
        String host = AppContext.get("server.host", "127.0.0.1");
        System.out.println("host = " + host);
        AppContext.set("last.status", status);
        AppContext.save();
		openIniFile(CONFIG_FILE);
		}
	*/

    // ── UI 폰트 ─────────────────────────────────────────────────
    /** 메인윈도우 전체 폰트 패밀리 (기본: Malgun Gothic) */
    public static String getUiFontFamily() {
        return get("ui.font.family", "Malgun Gothic");
    }
    public static void setUiFontFamily(String family) {
        set("ui.font.family", family != null ? family.trim() : "Malgun Gothic");
        save();
    }

    /** 메인윈도우 전체 폰트 크기 (기본: 13) */
    public static int getUiFontSize() {
        return getInt("ui.font.size", 13);
    }
    public static void setUiFontSize(int size) {
        setInt("ui.font.size", size > 0 ? size : 13);
        save();
    }

    /**
     * JavaFX Scene 전체에 폰트를 일괄 적용.
     * scene.getRoot().setStyle(...)로 하위 모든 노드에 상속.
     * MainWindow.theMainWindow() 또는 applyTheme() 호출 시 사용.
     */
    // =========================================================
    // [18] 즐겨찾기 슬롯 (마스터 INI 저장)
    //      키 형식: favorite.slot.{i}.name / favorite.slot.{i}.path
    //      MainWindow 업무도구(즐겨찾기) 메뉴에서 사용.
    // =========================================================
    public  static final int FAVORITE_SLOT_COUNT = 20;
    private static String favNameKey(int i) { return "favorite.slot." + i + ".name"; }
    private static String favPathKey(int i) { return "favorite.slot." + i + ".path"; }

    /** i번 슬롯 이름 조회 */
    public static String getFavoriteName(int i) {
        return get(favNameKey(i), "");
    }
    /** i번 슬롯 경로 조회 */
    public static String getFavoritePath(int i) {
        return get(favPathKey(i), "");
    }
    /** i번 슬롯 등록 후 저장 */
    public static void setFavorite(int i, String name, String path) {
        set(favNameKey(i), name  != null ? name  : "");
        set(favPathKey(i), path  != null ? path  : "");
        save();
    }
    /** i번 슬롯 삭제 후 저장 */
    public static void removeFavorite(int i) {
        remove(favNameKey(i));
        remove(favPathKey(i));
        save();
    }
    /** 비어있는 첫 슬롯 인덱스 반환 (없으면 FAVORITE_SLOT_COUNT) */
    public static int nextEmptyFavoriteSlot() {
        for (int i = 0; i < FAVORITE_SLOT_COUNT; i++) {
            if (getFavoriteName(i).isEmpty() || getFavoritePath(i).isEmpty()) return i;
        }
        return FAVORITE_SLOT_COUNT;
    }

    public static void applyGlobalFont(javafx.scene.Scene scene) {
        if (scene == null) return;
        String family = getUiFontFamily();
        int    size   = getUiFontSize();
        scene.getRoot().setStyle(
            scene.getRoot().getStyle()
            + "-fx-font-family: '" + family + "';"
            + "-fx-font-size: " + size + "px;"
        );
    }

}