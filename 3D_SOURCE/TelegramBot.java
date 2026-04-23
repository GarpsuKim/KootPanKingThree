import javax.swing.SwingUtilities;    // processCapture / /screenshot 용 (AWT Robot 스레드 전환)
import java.awt.GraphicsEnvironment;  // processCapture Monitor Wed OK
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.awt.Desktop;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URI;
/**
	* TelegramBot - 텔레그램 Bot API Integrations 클래스
	*
	* 역할:
	*   - getUpdates 폴링으로 명령어 수신
	*   - 명령어 처리 (캡처/종료/재시작/cmd 등)
	*   - 메시지 및 File 전송
	*
	* KootPanKing 과의 결합은 CommandHandler 콜백 인터페이스로만 연결.
	* 이 클래스는 Swing/AWT 에 직접 의존Other지 않는다.
*/
public class TelegramBot {
	
	private static TelegramBot INSTANCE;   // lazy singletone
	private static final Object LOCK = new Object();   // lazy singletone
	
	// ── 콜백 인터페이스 ──────────────────────────────────────────
    /**
		* 명령어 처리 중 KootPanKing 의 기능이 필요할 때 호출되는 콜백.
		* KootPanKing 이 구현Other여 TelegramBot 생성 h 주입한다.
	*/
    public interface CommandHandler {
        /** 시계 패널 캡처 → PNG File 반환 */
        File captureClockScreen() throws Exception;
        /** All Theme 캡처 → PNG File 반환 */
        File captureFullScreen() throws Exception;
        /** 특정 Monitor 캡처 → PNG File 반환 (0-based index) */
        File captureMonitor(int index) throws Exception;
        /** PC Shutdown (Settings Save → Shutdown Mail → OS 셧다운) */
        void shutdownPC();
        /** PC Restart (Settings Save → Shutdown Mail → OS Restart) */
        void rebootPC();
        /** 시계 창 Show/숨김 Sat글 → 결과 상태 반환 (true=Show됨, false=숨겨짐) */
        // boolean toggleTrayWindow();
        /** Download된 Image Files을 PC Theme 서브 윈도우에 Show */
        void showImage(java.io.File imageFile);
        /** Download된 Media File을 wmplayer로 재생 */
        void playMedia(java.io.File mediaFile);
        /** Settings Save */
        void saveConfig();
        /** /text 로 Save된 Text File을 메인창 탭으로 열기 */
        void openTextFile(java.io.File file);
	}

	// ── 카메라 명령 핸들러 (CommandHandler 와 분리 — MainWindow 에서 등록) ──────
	public interface CameraHandler {
		/** /cam       — 10초마다 동영상 전송 (연속 루프 시작) */
		void sendCameraSnapshot(String chatId);
		/** /rec N     — N초 단발 클립 녹화 → mp4 전송 (0=중단) */
		void startCameraRec(String chatId, int seconds);
		/** /camHello  — 카메라 시작 + 10초 무한 루프 */
		void startContinuousRec(String chatId);
		/** /recstop   — 전송 중단 (카메라 유지) */
		void stopContinuousRec(String chatId);
		/** /camBye    — 마지막 클립 완료 후 카메라 종료 */
		void camBye(String chatId);
	}
	private volatile CameraHandler cameraHandler = null;
	/** MainWindow 에서 카메라 시작 시 등록, 종료 시 null 로 해제 */
	public void setCameraHandler(CameraHandler h) { this.cameraHandler = h; }
	private static final AtomicBoolean startupNoticeSent  = new AtomicBoolean(false);
	private static final AtomicBoolean shutdownNoticeSent = new AtomicBoolean(false);
	private final java.util.concurrent.ConcurrentHashMap<String, Integer> menuMessageIds = new java.util.concurrent.ConcurrentHashMap<>();
    // if (!startupNoticeSent.compareAndSet(false, true)) {		return;	}
    // if (!shutdownNoticeSent.compareAndSet(false, true)) {		return;	}
	
	private volatile boolean initialized = false;
	private String exeFilePath = "";
	private String logFilePath = "";
	
	private String telegramPathS = "";
	private java.io.File telegramPathF = null;
	
	private static String publicIp = null;
	
	// ── Settings 필드 (외부에서 직접 읽기/쓰기) ──────────────────────
    // Google Calendar 서비스 (외부에서 주입)
    public GoogleCalendarService calendarService = null;
    // Naver Calendar 서비스 (외부에서 주입)
    public NaverCalendarService  naverCalendarService = null;
    // 카카오 인스턴스 (외부에서 주입) - sendTelegram() 호출 h 카카오에도 동h 전송
    public Kakao kakao = null;
    // GmailSender 인스턴스 (외부에서 주입) - /cmd 결과가 잘릴 때 Gmail 전송
    public GmailSender gmailSender = null;
	
	private static String  botToken  = "";  // BotFather 에서 발급받은 Bot Token
    private static String  myChatId  = "";  // 허용된 Chat ID (보안) - 비어있으면 All 허용
    public volatile boolean polling = false; // 폴링 activation 여부
    public static String  appDir    = AppContext.theExeFile.getParent();
    // ── 내부 상태 ─────────────────────────────────────────────────
    private volatile long              lastUpdateId  = 0;    // 마지막 처리한 update_id
    private ScheduledExecutorService   pollScheduler = null; // 폴링 스케lines러
    // 멀티스레드 안전: /reboot, /down 확인 대기 명령
    private final AtomicReference<String> pendingCmd = new AtomicReference<>("");
    // 콜백 핸들러 (KootPanKing 이 구현)
    private final CommandHandler handler;
    // ── 생성자 ────────────────────────────────────────────────────
    private TelegramBot(CommandHandler handler) {   // lazy singletone , 반드h private
        this.handler = handler;
		System.out.println("[TelegramBot] TelegramBot(CommandHandler handler)");
	}
	
	public static TelegramBot getInstance(CommandHandler handler) { // lazy singletone
		System.out.println("[TelegramBot] getInstance(CommandHandler handler)");
		if (INSTANCE == null) {
			synchronized (LOCK) {
				if (INSTANCE == null) {
					INSTANCE = new TelegramBot(handler);
				}
			}
		}
		return INSTANCE;
	}
	//  🔥 handler 없는 접근용도 (Select)
	public static TelegramBot getInstance() {
		System.out.println("[TelegramBot] getInstance()");
		return INSTANCE;
	}
	
	public   void init() {
		// public synchronized  void init(IniController ini) {
		try {
			if (initialized) return;
			initialized = true;
			this.telegramPathS = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + File.separator + "TELEGRAM";
            this.telegramPathF = new java.io.File(this.telegramPathS);
            if (!telegramPathF.exists()) telegramPathF.mkdirs();
			this.botToken   = AppContext.get("tg.botToken", "");
			this.myChatId   = AppContext.get("tg.myChatId", "");
			
			this.exeFilePath = AppContext.theExePath;
			this.logFilePath = AppLogger.getLogFilePath();
			
			
			System.out.println("[TelegramBot] init()");
			} catch (Exception e) {
			System.out.println("[TelegramBot] init Failed: " + e.getMessage());
		}
	}
	
	public void sendShutdownNoticeOnce() {
		sendShutdownNoticeSync();
	}
	// ── 폴링 시작 / 중지 ─────────────────────────────────────────
    /** 폴링 시작. 어느 스레드에서든 호출 가능. */
    public void startPolling() {
        // System.out.println("[TelegramBot] startPolling() : before");
        stopPolling(); // 기존 스케lines러 정리
		if (botToken.isEmpty()){
			System.out.println("[TelegramBot] botToken.isEmpty()");
		}
		if (!polling) {
			System.out.println("[TelegramBot] !polling");
		}
        if (!polling || botToken.isEmpty()) return;
        skipOldUpdates();
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
	        System.out.println("[TelegramBot] pollScheduler = Executors.newSingleThreadScheduledExecutor");
			
            Thread t = new Thread(r, "TelegramPoll");
            t.setDaemon(true);
            return t;
		});
        pollScheduler.scheduleAtFixedRate(() -> {
	        // System.out.println("[TelegramBot] pollScheduler.scheduleAtFixedRate ");
            try { poll(); }
            catch (Exception e) {
                System.out.println("[Telegram] polling exception: " + e.getMessage());
			}
		}, 0, 5, TimeUnit.SECONDS);
        // System.out.println("[Telegram] polling start (5sec interval)");
	}
    /** 폴링 Stop. 어느 스레드에서든 호출 가능. */
    public void stopPolling() {
		if (pollScheduler == null) {
	        System.out.println("[Telegram] polling was already stopped");
            return ;
		}
	    try {
			if (pollScheduler != null) {
				System.out.println("[TelegramBot] pollScheduler.shutdownNow() ");
				pollScheduler.shutdownNow();
				pollScheduler = null;
		        System.out.println("[Telegram] polling stop successful");
			}
			} catch (Exception e) {
	        System.out.println("[Telegram] polling shutdown Failed");
		AppLogger.logException(e);		}
	}
	/** 폴링 시작 전 기존 Message를 모두 건너뜀 - Restart 후 이전 명령 재처리 방지 */
	private void skipOldUpdates() {
		try {
			String apiUrl = "https://api.telegram.org/bot" + botToken
            + "/getUpdates?timeout=0&offset=-1";
			HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(8000);
			con.setReadTimeout(8000);
			if (con.getResponseCode() == 200) {
				java.io.BufferedReader br = new java.io.BufferedReader(
				new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) sb.append(line);
				con.disconnect();
				// 가장 마지막 update_id File싱
				java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"update_id\":(\\d+)")
                .matcher(sb.toString());
				while (m.find())
                lastUpdateId = Long.parseLong(m.group(1));
				System.out.println("[Telegram] existing messages ignore done (lastUpdateId=" + lastUpdateId + ")");
			}
			} catch (Exception e) {
			System.out.println("[Telegram] skipOldUpdates Failed: " + e.getMessage());
		}
	}
	/*	
		private void skipOldUpdates_Error() {
		try {
		String apiUrl = "https://api.telegram.org/bot" + botToken
		+ "/getUpdates?timeout=0&offset=-1";
		HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
		con.setRequestMethod("GET");
		con.setConnectTimeout(8000);
		con.setReadTimeout(8000);
		if (con.getResponseCode() == 200) {
		java.io.ByteArrayOutputStream skipBaos = new java.io.ByteArrayOutputStream();
		byte[] skipBuf = new byte[4096]; int skipN;
		try (java.io.InputStream skipIs = con.getInputStream()) {
		while ((skipN = skipIs.read(skipBuf)) != -1) skipBaos.write(skipBuf, 0, skipN);
		}
		con.disconnect();
		try {
		JSONObject r = (JSONObject) new JSONParser().parse(skipBaos.toString("UTF-8"));
		JSONArray arr = (JSONArray) r.get("result");
		if (arr != null) for (Object o : arr) {
		long uid = (Long)((JSONObject)o).get("update_id");
		if (uid > lastUpdateId) lastUpdateId = uid;
		}
		} catch (Exception pe) {}
		System.out.println("[Telegram] existing messages ignore done (lastUpdateId=" + lastUpdateId + ")");
		} catch (Exception e) {
		System.out.println("[Telegram][sendStartupNotice] Sending Failed: " + e.getMessage());
		}
		}, "TelegramStartup").start();
		}
	*/
	
    // ── Startup Notice ─────────────────────────────────────────────────
    /** 앱 Startup Notice 전송 (비동기) */
    public void sendStartupNotice() {
        System.out.println("[TelegramBot] pollScheduler.shutdownNow() ");
        if (botToken.isEmpty()) {
	        System.out.println("[TelegramBot] botToken.isEmpty() ");
		}
        if (myChatId.isEmpty()) {
	        System.out.println("[TelegramBot] myChatId.isEmpty() ");
		}
        if (botToken.isEmpty() || myChatId.isEmpty()) {
            System.out.println("[Telegram][sendStartupNotice] skip — botToken=" + (botToken.isEmpty() ? "(missing)" : "(present)")
			+ " myChatId=" + (myChatId.isEmpty() ? "(none)" : myChatId));
            return;
		}
	    if (!startupNoticeSent.compareAndSet(false, true)) {
			System.out.println("[TelegramBot] startupNoticeSent.compareAndSet(false, true) ");
			return;
		}
		new Thread(() -> {
            try {
                String now     = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                String pcName  = java.net.InetAddress.getLocalHost().getHostName();
                String userId  = System.getProperty("user.name");
                String osName  = System.getProperty("os.name") + " " + System.getProperty("os.version");
                String javaVer = System.getProperty("java.version");
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                publicIp = getPublicIp();
                String msg = "🟢 PC started.\n\n"
				+ "🕐 Started at: " + now + "\n"
				+ "💻 PC Name  : " + pcName  + "\n"
				+ "👤 User     : " + userId  + "\n"
				+ "🌐 IP (LAN) : " + localIp  + "\n"
				+ "🌍 IP (WAN) : " + publicIp + "\n"
				+ "🖥 OS       : " + osName  + "\n"
				+ "☕ Java     : " + javaVer;
                System.out.println("[Telegram][sendStartupNotice] chatId=" + myChatId);
                sendTelegram(msg);
                // ── Control Panel 버튼 전송 ──────────────────────────
                sendBrowserButton(myChatId);

				processCapture  ( myChatId,  0, false);
				processCapture  ( myChatId,  1, false);
				processCapture  ( myChatId,  2, false);
				processCapture  ( myChatId,  3, false);
				// ── 붙박이 명령 메뉴 (항상 채팅창 하단에 표시) ───────
				sendHelpMenu(myChatId);          // 여러줄 명령어 버튼
				sendOrRefreshMainMenu(myChatId); // 카테고리 네비게이션 메뉴
                System.out.println("[Telegram][sendStartupNotice] Sending Done → " + myChatId);
				} catch (Exception e) {
                System.out.println("[Telegram][sendStartupNotice] Sending Failed: " + e.getMessage());
			}
		}, "TelegramStartup").start();
	}	
	
	public void sendTelegramExit() {
		if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
			KootPanKingThreeLaunch.aliveStatusAgent.stop();
		}
		String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
		try {
			String pc0 = System.getenv("COMPUTERNAME");
			if (pc0 == null || pc0.trim().isEmpty()) pc0 = java.net.InetAddress.getLocalHost().getHostName();
			if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
				KootPanKingThreeLaunch.aliveStatusAgent.updatePinnedMessagesNow("⚠️ [FORCE SHUTDOWN DETECTED] " + pc0 + " " + now);
			}
		} catch (Exception e) {
			System.out.println("[Telegram][sendTelegramExit] pin update failed: " + e.getMessage());
		}
		String pcName = System.getenv("COMPUTERNAME");   // Windows();
		String userId = System.getProperty("user.name", "(unknown)");
		String msg = "⚠️ Force shutdown detected!\n\n"
		+ "🕐 Time    : " + now    + "\n"
		+ "💻 PC      : " + pcName + "\n"
		+ "👤 User    : " + userId + "\n\n"
		+ "📋 Reason  : Windows shutdown/restart or kill signal";
		sendTelegram(msg);
		processCapture  ( myChatId,  0, false);
		processCapture  ( myChatId,  1, false);
		processCapture  ( myChatId,  2, false);
		processCapture  ( myChatId,  3, false);
	}
	/** Shutdown/Restart Notice Telegram 전송 (비동기) */
	public void sendShutdownNotice() { sendShutdownNotice(false); }
	public void sendShutdownNotice(boolean reboot) {
		if (botToken.isEmpty() || myChatId.isEmpty()) {
			System.out.println("[Telegram][sendShutdownNotice] skip — botToken=" + (botToken.isEmpty() ? "(missing)" : "(present)")
			+ " myChatId=" + (myChatId.isEmpty() ? "(none)" : myChatId));
			return;
		}
		if (!shutdownNoticeSent.compareAndSet(false, true)) {		return;	}
		if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
			KootPanKingThreeLaunch.aliveStatusAgent.stop();
		}
		new Thread(() -> {
			try {
				String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				try {
					String pinPc = java.net.InetAddress.getLocalHost().getHostName();
					String pinText = (reboot ? "🔄 [RESTARTING] " : "🔴 [SHUTTING DOWN] ") + pinPc + " " + now;
					if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
						KootPanKingThreeLaunch.aliveStatusAgent.updatePinnedMessagesNow(pinText);
					}
				} catch (Exception pinEx) {
					System.out.println("[Telegram][sendShutdownNotice] pin update failed: " + pinEx.getMessage());
				}
				String pcName = java.net.InetAddress.getLocalHost().getHostName();
				String userId = System.getProperty("user.name");
				String msg = (reboot ? "🔄 PC is restarting." : "🔴 PC is shutting down.") + "\n\n"
				+ "🕐 " + (reboot ? "Restart" : "Shutdown") + " at: " + now + "\n"
				+ "💻 PC Name  : " + pcName + "\n"
				+ "👤 User     : " + userId;
				System.out.println("[Telegram][sendShutdownNotice] chatId=" + myChatId);
				System.out.println("[Telegram][sendShutdownNotice] body=\n" + msg);
				sendTelegram( msg);
				sendOrRefreshMainMenu(myChatId);
				System.out.println("[Telegram][sendShutdownNotice] Sending Done → " + myChatId);
				} catch (Exception e) {
				System.out.println("[Telegram][sendShutdownNotice] Sending Failed: " + e.getMessage());
			}
		}, "TelegramShutdown").start();
	}
	/**
		* Shutdown Notice 동기 전송 (호출 스레드에서 Done까지 대기).
		* sendShutdownEmailAndExit() 에서 Gmail 보다 먼저 Done를 보장Other기 위해 Enable.
		* 텔레그램 미Settings h 즉h 반환.
	*/
	public void sendShutdownNoticeSync() {
		if (botToken.isEmpty() || myChatId.isEmpty()) {
			System.out.println("[Telegram][sendShutdownNoticeSync] skip — botToken=" + (botToken.isEmpty() ? "(missing)" : "(present)")
			+ " myChatId=" + (myChatId.isEmpty() ? "(none)" : myChatId));
			return;
		}
		if (!shutdownNoticeSent.compareAndSet(false, true)) {		return;	}
		if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
			KootPanKingThreeLaunch.aliveStatusAgent.stop();
		}
		try {
			String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			try {
				String pinPc = java.net.InetAddress.getLocalHost().getHostName();
				if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
					KootPanKingThreeLaunch.aliveStatusAgent.updatePinnedMessagesNow("🔴 [SHUTTING DOWN] " + pinPc + " " + now);
				}
			} catch (Exception pinEx) {
				System.out.println("[Telegram][sendShutdownNoticeSync] pin update failed: " + pinEx.getMessage());
			}
			String pcName = java.net.InetAddress.getLocalHost().getHostName();
			String userId = System.getProperty("user.name");
			String msg = "🔴 PC is shutting down.\n\n"
			+ "🕐 Shutdown at: " + now + "\n"
			+ "💻 PC Name  : " + pcName + "\n"
			+ "👤 User     : " + userId;
			System.out.println("[Telegram][sendShutdownNoticeSync] chatId=" + myChatId);
			System.out.println("[Telegram][sendShutdownNoticeSync] body=\n" + msg);
			sendTelegram( msg);
			System.out.println("[Telegram][sendShutdownNoticeSync] Sending Done → " + myChatId);
			} catch (Exception e) {
			System.out.println("[Telegram][sendShutdownNoticeSync] Sending Failed: " + e.getMessage());
		}
	}
	/** 외부 공인 IP 조회 (api.ipify.org Enable) */
	private String getPublicIp() {
		if  ( publicIp != null ) return publicIp;
		
		try {
			// java.net.URL url = new java.net.URL("https://api.ipify.org");
			java.net.URL url = toUrl("https://api.ipify.org");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
			String ip = br.readLine();
			br.close();
			con.disconnect();
			return (ip != null && !ip.isEmpty()) ? ip.trim() : "(lookup failed)";
			} catch (Exception e) {
			return "(lookup failed)";
		}
	}
	// ── getUpdates 폴링 ──────────────────────────────────────────
	private void poll() {
		// System.out.println("[TelegramBot] poll() ");
		if (botToken.isEmpty()) {
			System.out.println("[TelegramBot] poll() : (botToken.isEmpty()) ");
			return;
		}
		try {
			String apiUrl = "https://api.telegram.org/bot" + botToken
			+ "/getUpdates?timeout=1&offset=" + (lastUpdateId + 1);
			HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(8000);
			con.setReadTimeout(8000);
			int responseCode = con.getResponseCode();
			if (responseCode != 200) {
				System.out.println("[Telegram Poll] HTTP " + responseCode);
				con.disconnect();
				return;
			}
			// readLine() 대신 InputStream 직접 읽기 — newline 포함 긴 Text 대응
			java.io.InputStream is = con.getInputStream();
			byte[] buf = new byte[8192];
			java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			int n;
			while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
			con.disconnect();
			String jsonStr = baos.toString("UTF-8");
			System.out.println("[Telegram Poll] response: " + jsonStr.substring(0, Math.min(500, jsonStr.length())));
			
			// json-simple File싱 — 한글/이스케이프/lines바꿈 자동 처리
			JSONObject root = (JSONObject) new JSONParser().parse(jsonStr);
			if (!Boolean.TRUE.equals(root.get("ok"))) return;
			JSONArray results = (JSONArray) root.get("result");
			if (results == null || results.isEmpty()) return;
			
			for (Object updateObj : results) {
				JSONObject update = (JSONObject) updateObj;
				long updateId = (Long) update.get("update_id");
				if (updateId <= lastUpdateId) continue;
				lastUpdateId = updateId;
				
				// edited_message — peer 서버 heartbeat 감지
				JSONObject editedMsg = (JSONObject) update.get("edited_message");
				if (editedMsg != null) {
					Object msgIdObj = editedMsg.get("message_id");
					if (msgIdObj != null && KootPanKingThreeLaunch.aliveStatusAgent != null) {
						long peerMsgId = ((Number) msgIdObj).longValue();
						KootPanKingThreeLaunch.aliveStatusAgent.onPeerMessageEdited(peerMsgId);
					}
					continue;
				}

				// CallbackQuery 처리
				JSONObject cbQuery = (JSONObject) update.get("callback_query");
				if (cbQuery != null) {
					String queryId = (String) cbQuery.get("id");
					JSONObject cbFrom = (JSONObject) cbQuery.get("from");
					if (cbFrom == null) continue;
					String fromId = String.valueOf(cbFrom.get("id"));
					if (!myChatId.isEmpty() && !fromId.equals(myChatId)) continue;
					String data = (String) cbQuery.get("data");
					if (data == null) continue;
					answerCallbackQuery(queryId);
					processCallbackData(fromId, data);
					continue;
				}
				
				// 반 message 처리
				JSONObject message = (JSONObject) update.get("message");
				if (message == null) continue;
				JSONObject chat = (JSONObject) message.get("chat");
				if (chat == null) continue;
				String fromChatId = String.valueOf(chat.get("id"));
				/*
				if (!myChatId.isEmpty() && !fromChatId.equals(myChatId)) {
					sendTelegramWarning(fromChatId, "❌ Unauthorized access.");
					continue;
				}
				*/
				
				String text = (String) message.get("text");
				if (text == null) {
					receiveFileFromJson(fromChatId, message);
					continue;
				}

				// ── peer 자동 발견: [ALIVE_HELLO] serverName:groupAliveMessageId ──
				if (text.startsWith("[ALIVE_HELLO] ")) {
					String payload = text.substring("[ALIVE_HELLO] ".length()).trim();
					int colon = payload.lastIndexOf(':');
					if (colon > 0) {
						try {
							String peerName  = payload.substring(0, colon).trim();
							long   peerMsgId = Long.parseLong(payload.substring(colon + 1).trim());
							if (KootPanKingThreeLaunch.aliveStatusAgent != null) {
								KootPanKingThreeLaunch.aliveStatusAgent.onHelloReceived(peerName, peerMsgId);
							}
						} catch (Exception ignored) {}
					}
					continue;
				}
				
				// 1. 명령어 최우선 검증
				int sp = text.indexOf(' ');
				String firstWord = (sp > 0 ? text.substring(0, sp) : text).toLowerCase();
				System.out.println("[Telegram Poll] chatId=" + fromChatId + " cmd=" + firstWord);
				
				if (firstWord.startsWith("/")) {
					if (!VALID_CMDS.contains(firstWord)) {
						System.out.println("[Telegram CMD] invalid command: " + firstWord);
						sendTelegram("❓ Unknown command: " + firstWord + "\n/help for command list");
						continue;
					}
					// 2. /text: All 바디를 데이터로 바로 Save
					if (firstWord.equals("/text")) {
						String body = sp > 0 ? text.substring(sp + 1).trim() : "";
						saveBodyDirectly(fromChatId, body);
						continue;
					}
				}
				
				// 3. 유효 명령어 처리
				processCommand(fromChatId, text.trim());
			}
			} catch (Exception e) {
			System.out.println("[Telegram Poll] error: " + e.getMessage());
		}
	}
	
	// ── 명령어 처리 ───────────────────────────────────────────────
	// 유효한 명령어 집합 — 여기 없으면 즉h 반환
	private static final java.util.Set<String> VALID_CMDS = new java.util.HashSet<>(java.util.Arrays.asList(
		"/ps", "/cmd", "/save", "/wh", "/start", "/yes", "/no", "/n",
		"/c", "/capture", "/c1", "/c2", "/c3", "/c4",
		"/s", "/screenshot",
		"/d", "/down", "/r", "/reboot",
		"/h", "/help",
		"/text", "/logout_calendar",
		"/myschedule", "/ms", "/naverschedule", "/ns",
		"/menu", "/app",
		"/cam", "/camhello", "/recstop", "/cambye", "/rec"
	));
	
	private void processCommand(String chatId, String text) {
		// 1. 첫번치 space로 명령어 추출
		int sp = text.indexOf(' ');
		String cmd = (sp > 0 ? text.substring(0, sp) : text).toLowerCase();
		// 2. 명령어 유효성 최우선 검증 — 유효한 명령어 때만 나머지 처리
		if (cmd.isEmpty()) return;
		if (!VALID_CMDS.contains(cmd)) {
			System.out.println("[Telegram CMD] unknown command: " + cmd);
			sendTelegram("❓ Unknown command.\n Type /help to see the command list.");
			return;
		}
		// 3. 유효한 명령어 때만 실제 처리
		System.out.println("[Telegram CMD] " + chatId + " → " + cmd);
		switch (cmd) {
			case "/cmd"   : processCMD  ( chatId,  text);	break;
			case "/ps"   : processPowerShell  ( chatId,  text);	break;
			
			case "/save"  : processSave ( chatId,  text);	break;
			case "/wh"    : processStart( chatId,  text);	break;
			case "/start" : processStart( chatId,  text);	break;
			case "/yes"   : processYes  ( chatId,  text);	break;
			// case "/tray"  : processTray ( chatId,  text);	break;
			
			case "/c1":	processCapture  ( chatId,  0);	break;
			case "/c2":	processCapture  ( chatId,  1);	break;
			case "/c3":	processCapture  ( chatId,  2);	break;
			case "/c4":	processCapture  ( chatId,  3);	break;
			
			case "/h":
				sendHelpMenu(chatId); // 인라인 버튼 도움말만 — 붙박이 메뉴 건드리지 않음
				break;

			case "/help":
				sendHelpText(chatId);
				break;

			case "/menu":
				sendOrRefreshMainMenu(chatId);
				break;
			case "/app":
				sendBrowserButton(chatId);
				break;

			case "/cam":
				if (cameraHandler != null) cameraHandler.startContinuousRec(chatId);
				else autoConnectAndStartContinuousRec(chatId);
				break;

			case "/rec": {
				// /rec        → 기본 10초 단발
				// /rec N      → N초 (1~60)
				// /rec stop   → 진행 중인 단발 녹화 중단
				String arg = text.length() > 4 ? text.substring(4).trim() : "";
				if ("stop".equalsIgnoreCase(arg)) {
					if (cameraHandler != null) cameraHandler.startCameraRec(chatId, 0);
					else sendTelegram("❌ 카메라가 연결되지 않았습니다");
				} else {
					int sec = 10;
					try { sec = Integer.parseInt(arg); } catch (Exception ignored) {}
					sec = Math.max(1, Math.min(60, sec));
					if (cameraHandler != null) cameraHandler.startCameraRec(chatId, sec);
					else sendTelegram("❌ 카메라가 연결되지 않았습니다");
				}
				break;
			}

			case "/camhello":
				if (cameraHandler != null) {
					// 카메라가 이미 연결된 경우: 현재 프레임 즉시 스냅샷 전송
					cameraHandler.sendCameraSnapshot(chatId);
				} else {
					// 카메라 미연결: 저장된 URL로 자동 연결 → 사진 1장 촬영 → 전송
					autoConnectAndSendSnapshot(chatId);
				}
				break;

			case "/recstop":
				if (cameraHandler != null) cameraHandler.stopContinuousRec(chatId);
				else sendTelegram("❌ 카메라가 연결되지 않았습니다");
				break;

			case "/cambye":
				if (cameraHandler != null) cameraHandler.camBye(chatId);
				else sendTelegram("❌ 카메라가 연결되지 않았습니다");
				break;
			case "/c":
			case "/capture":
			sendTelegram( "📷 Capturing clock screen...");
			try {
				sendFile(chatId, handler.captureClockScreen());
				} catch (Exception ex) {
				sendTelegram( "❌ Capture failed: " + ex.getMessage());
			}
			break;
			
			case "/s":
			case "/screenshot":
			sendTelegram( "🖥 Capturing full screen...");
			/*
				new Thread(() -> {
				try   { sendFile(chatId, handler.captureFullScreen()); }
				catch (Exception ex) { sendTelegram(chatId, "❌ Full screen capture failed: " + ex.getMessage()); }
				}, "ScreenCapture").start();
			*/
			new Thread(() -> {
				try {
					final File[] result = new File[1];
					SwingUtilities.invokeAndWait(() -> {
						try { result[0] = handler.captureFullScreen(); }
						catch (Exception e) { System.out.println("[Capture] " + e.getMessage()); }
					});
					if (result[0] != null) sendFile(chatId, result[0]);
				} catch (Exception e) { sendTelegram("❌ Full screen capture failed: " + e.getMessage()); }
			}, "ScreenCapture").start();
			break;
			
			case "/d":
			case "/down":
			pendingCmd.set("/down");
			sendTelegram( "⚠️ Shutdown PC?\n/yes - Confirm\n/no  - Cancel");
			break;
			
			case "/r":
			case "/reboot":
			pendingCmd.set("/reboot");
			sendTelegram( "🔄 Reboot PC?\n/yes - Confirm\n/no  - Cancel");
			break;
			
			case "/n":
			case "/no": {
				// getAndSet("") : 읽기와 initialization를 원자적으로 처리
				String cancelled = pendingCmd.getAndSet("");
				if (!cancelled.isEmpty()) sendTelegram( "✅ " + cancelled + " cancelled.");
				else                      sendTelegram( "❓ No pending command.");
				break;
			}
			
			case "/logout_calendar":
			processLogoutCalendar(chatId);
			break;
			
			case "/text":
			processText(chatId, text);
			break;
			
			case "/myschedule":
			case "/ms":
			processMySchedule(chatId, text);
			break;
			
			case "/naverschedule":
			case "/ns":
			processNaverSchedule(chatId, text);
			break;
			
		}  //  switch
	}  //  processCommand

	/**
	 * /camhello — 카메라 미연결 상태에서 자동 연결 → 사진 1장 촬영 → 텔레그램 전송 → 연결 해제.
	 * AppContext.getCameraUrl() 에 저장된 URL (IP Webcam MJPEG 스트림) 을 사용한다.
	 */
	private void autoConnectAndSendSnapshot(String chatId) {
		new Thread(() -> {
			String url = AppContext.getCameraUrl();
			if (url == null || url.trim().isEmpty()) {
				sendTelegram("❌ 카메라 URL이 설정되지 않았습니다\n메인창 > Phone Camera > Connect 에서 URL을 먼저 설정하세요");
				return;
			}
			sendTelegram("📷 카메라 연결 중...\n" + url);
			TOOLS.CaptureManager.Camera tmpCam = new TOOLS.CaptureManager.Camera(frame -> { /* UI 없음 */ });
			try {
				tmpCam.start(url);
				// 첫 프레임 도착 대기 (최대 8초, 200ms 간격)
				long deadline = System.currentTimeMillis() + 8_000L;
				while (tmpCam.getLastFrameAWT() == null && System.currentTimeMillis() < deadline) {
					Thread.sleep(200);
				}
				if (tmpCam.getLastFrameAWT() == null) {
					sendTelegram("❌ 카메라 연결 실패 (타임아웃 8초)\nURL을 확인하세요: " + url);
					return;
				}
				// 스냅샷 캡처 → APP_DIR/tg_rec/snap_hello/
				java.io.File snapDir = new java.io.File(AppContext.getAPP_DIR(), "tg_rec/snap_hello");
				snapDir.mkdirs();
				String saved = tmpCam.capture(snapDir);
				if (saved != null) {
					sendFile(chatId, new java.io.File(saved));
					System.out.println("[TgCamHello] snapshot sent: " + saved);
				} else {
					sendTelegram("❌ 스냅샷 캡처 실패");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				sendTelegram("❌ /camhello 오류: " + e.getMessage());
				System.out.println("[TgCamHello] error: " + e.getMessage());
			} finally {
				tmpCam.stop();
			}
		}, "TgCamHello").start();
	}

	/**
	 * /cam — 카메라 미연결 상태에서 자동 연결 → TelegramCamRecorder 생성 → 10초 연속 루프 시작.
	 * 생성된 핸들러를 setCameraHandler() 로 등록하여 /recstop, /cambye 도 정상 동작한다.
	 */
	private void autoConnectAndStartContinuousRec(String chatId) {
		new Thread(() -> {
			String url = AppContext.getCameraUrl();
			if (url == null || url.trim().isEmpty()) {
				sendTelegram("❌ 카메라 URL이 설정되지 않았습니다\n메인창 > Phone Camera > Connect 에서 URL을 먼저 설정하세요");
				return;
			}
			sendTelegram("📷 카메라 연결 중...\n" + url);
			TOOLS.CaptureManager.Camera tmpCam = new TOOLS.CaptureManager.Camera(frame -> { /* UI 없음 */ });
			try {
				tmpCam.start(url);
				// 첫 프레임 도착 대기 (최대 8초)
				long deadline = System.currentTimeMillis() + 8_000L;
				while (tmpCam.getLastFrameAWT() == null && System.currentTimeMillis() < deadline) {
					Thread.sleep(200);
				}
				if (tmpCam.getLastFrameAWT() == null) {
					sendTelegram("❌ 카메라 연결 실패 (타임아웃 8초)\nURL을 확인하세요: " + url);
					tmpCam.stop();
					return;
				}
				// TelegramCamRecorder 생성 후 cameraHandler 로 등록
				java.io.File recWorkDir = new java.io.File(AppContext.getAPP_DIR(), "tg_rec");
				Multimedia.TelegramCamRecorder rec = new Multimedia.TelegramCamRecorder(tmpCam, this, recWorkDir);
				setCameraHandler(new CameraHandler() {
					@Override public void sendCameraSnapshot(String cid) { rec.sendSnapshot(cid); }
					@Override public void startCameraRec(String cid, int sec) {
						if (sec == 0) rec.stopRec(cid); else rec.startRec(cid, sec);
					}
					@Override public void startContinuousRec(String cid) { rec.startContinuousRec(cid); }
					@Override public void stopContinuousRec(String cid)   { rec.stopContinuousRec(cid); }
					@Override public void camBye(String cid) {
						rec.camBye(cid);
						// 마지막 클립 완료 후 카메라 해제
						new Thread(() -> {
							rec.waitForStop();
							tmpCam.stop();
							setCameraHandler(null);
							System.out.println("[AutoCam] camera stopped & handler cleared");
						}, "AutoCamByeWaiter").start();
					}
				});
				// 연속 루프 시작
				rec.startContinuousRec(chatId);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				tmpCam.stop();
			} catch (Exception e) {
				sendTelegram("❌ /cam 자동연결 오류: " + e.getMessage());
				System.out.println("[AutoCam] error: " + e.getMessage());
				tmpCam.stop();
			}
		}, "TgCamAutoConnect").start();
	}

	/** /logout_calendar 명령 처리 - Google 캘린더 로그아웃 */
	/** /text [내용] → TELEGRAM Folder에 Save + 메인창 탭으로 열기 */
	/** /text 처리: regex 쵆이 JSON block에서 body 추출 후 Save. All 바디를 데이터로 처리 */
	private void saveTextDirectly(String chatId, String block, int textFieldIdx) {
		if (telegramPathF == null) {
			String dir = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + java.io.File.separator + "TELEGRAM";
			telegramPathF = new java.io.File(dir);
			if (!telegramPathF.exists()) telegramPathF.mkdirs();
		}
		try {
			if (textFieldIdx < 0) { sendTelegram("❌ /text: Failed to extract text field"); return; }
			/*  "text":"  — 8-char skip, "\/text " or "/text " skip  */
			int pos = textFieldIdx + 8;
			if (pos < block.length() && block.charAt(pos) == '\\') pos++;  
			if (pos < block.length() && block.charAt(pos) == '/')  pos++;  
			while (pos < block.length() && block.charAt(pos) != ' ' && block.charAt(pos) != '"') pos++; // "text" skip
			if (pos < block.length() && block.charAt(pos) == ' ') pos++;  // 공백 skip
			
			// JSON escape → 실제 문자 변환
			StringBuilder body = new StringBuilder();
			while (pos < block.length()) {
				char c = block.charAt(pos);
				if (c == '"') break;
				if (c == '\\' && pos + 1 < block.length()) {
					char nx = block.charAt(pos + 1);
					if      (nx == 'n')  { body.append('\n');  pos += 2; }
					else if (nx == 'r')  {                       pos += 2; }
					else if (nx == 't')  { body.append('\t');  pos += 2; }
					else if (nx == '"')  { body.append('"');   pos += 2; }
					else if (nx == '/')  { body.append('/');   pos += 2; }
					else if (nx == '\\') { body.append('\\'); pos += 2; }
					else if (nx == 'u' && pos + 5 < block.length()) {
						try { int cp = Integer.parseInt(block.substring(pos+2, pos+6), 16); body.append((char)cp); }
						catch (NumberFormatException ignored) {}
						pos += 6;
					} else { body.append(c); pos++; }
				} else { body.append(c); pos++; }
			}
			String bodyStr = body.toString().trim();
			if (bodyStr.isEmpty()) { sendTelegram("❌ /text: Body is empty"); return; }
			
			String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
			java.io.File outFile = new java.io.File(telegramPathF, "Telegram_" + ts + ".txt");
			try (java.io.Writer w = new java.io.OutputStreamWriter(
			new java.io.FileOutputStream(outFile), java.nio.charset.StandardCharsets.UTF_8)) {
			w.write(bodyStr);
			}
			System.out.println("[saveTextDirectly] Save: " + outFile.getAbsolutePath());
			sendTelegram("✅ Saved: " + outFile.getName());
			final java.io.File ff = outFile;
			javafx.application.Platform.runLater(() -> handler.openTextFile(ff));
			} catch (Exception e) {
			sendTelegram("❌ Save failed: " + e.getMessage());
			System.out.println("[saveTextDirectly] " + e.getMessage());
		}
	}
	
	private void processText(String chatId, String rawText) {
		// "/text " 이후 문자열 추출 (trim)
		String body = rawText.length() > 5 ? rawText.substring(5).trim() : "";
		if (body.isEmpty()) {
			sendTelegram("❌ Usage: /text <message to save>");
			return;
		}
		// [유니코드6자] 유니코드 이스케이프 → 실제 문자 변환
		body = decodeUnicodeEscapes(body);
		// telegramPathF null 방어 — init() 미호출 h APP_DIR\TELEGRAM 으로 fallback
		if (telegramPathF == null) {
			String dir = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + java.io.File.separator + "TELEGRAM";
			telegramPathF = new java.io.File(dir);
			if (!telegramPathF.exists()) telegramPathF.mkdirs();
		}
		System.out.println("[processText] Save Folder: " + telegramPathF.getAbsolutePath());
		// File명: Telegram_yyyyMMdd_HHmmss.txt
		String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
		java.io.File outFile = new java.io.File(telegramPathF, "Telegram_" + ts + ".txt");
		try {
			try (java.io.Writer w = new java.io.OutputStreamWriter(
			new java.io.FileOutputStream(outFile), java.nio.charset.StandardCharsets.UTF_8)) {
			w.write(body);
			}
			sendTelegram("✅ Saved: " + outFile.getName());
			final java.io.File finalFile = outFile;
			javafx.application.Platform.runLater(() -> handler.openTextFile(finalFile));
			} catch (Exception e) {
			sendTelegram("❌ Save failed: " + e.getMessage());
			System.out.println("[TelegramBot.processText] " + e.getMessage());
		}
	}
	
	/** JSON [유니코드6자] 유니코드 이스케이프 시퀀스를 실제 문자로 변환 */
	private static String decodeUnicodeEscapes(String s) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			if (i + 5 < s.length()
				&& s.charAt(i) == '\\' && s.charAt(i + 1) == 'u'
				&& isHex(s.charAt(i+2)) && isHex(s.charAt(i+3))
				&& isHex(s.charAt(i+4)) && isHex(s.charAt(i+5))) {
				int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
				sb.append((char) cp);
				i += 6;
				} else {
				sb.append(s.charAt(i));
				i++;
			}
		}
		return sb.toString();
	}
	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}
	
	private void processLogoutCalendar(String chatId) {
		System.out.println("[TelegramBot] processLogoutCalendar() ");
		
		
		if (calendarService == null) {
			System.out.println("[TelegramBot] processLogoutCalendar()Google Calendar service is not connected ");
			sendTelegram( "❌ Google Calendar service is not connected.");
			return;
		}
		String deletedPath = calendarService.logout();
		if (deletedPath != null) {
			sendTelegram( "✅ Google Calendar logout complete\n"
				+ "🗑 Token deleted: " + deletedPath + "\n\n"
			+ "Browser authentication will be required on next startup.");
			} else {
			sendTelegram( "✅ Google Calendar logout complete\n"
			+ "(No saved token file)");
		}
	}
	
	/** /mySchedule 명령 처리 - 인라인 버튼 메뉴 또는 직접 조회 */
	private void processMySchedule(String chatId, String text) {
		if (calendarService == null || !calendarService.isInitialized()) {
			System.out.println("[TelegramBot] Google Calendar integration is not configured ");
			sendTelegram( "❌ Google Calendar is not configured.");
			return;
		}
		String[] parts = text.trim().split("\\s+");
		if (parts.length == 1) {
			sendWithInlineKeyboard(chatId,
				"📅 Select schedule query range:",
				new String[][]{
					{"Today",     "ms_today"},
					{"Tomorrow",     "ms_tomorrow"},
					{"Next 3 days", "ms_3"},
					{"Next 7 days", "ms_7"},
					{"Past 7 days", "ms_week"},
					{"This month",  "ms_month"},
					{"Next month",  "ms_nextmonth"}
				});
				return;
		}
		fetchAndSendSchedule(chatId, parts[1].toLowerCase());
	}
	
	/** schedule 조회 및 전송 */
	private void fetchAndSendSchedule(String chatId, String arg) {
		System.out.println("[TelegramBot] fetchAndSendSchedule ");
		
		new Thread(() -> {
			try {
				java.util.List<GoogleCalendarService.CalendarEvent> events;
				String title;
				switch (arg) {
					case "today":
					case "Today":
					events = calendarService.getToday();
					title  = "Today's schedule (" + java.time.LocalDate.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("M/d")) + ")";
					break;
					case "tomorrow":
					case "Tomorrow":
					events = calendarService.getNextDays(2).stream()
					.filter(e -> e.startTime.toLocalDate()
					.equals(java.time.LocalDate.now().plusDays(1)))
					.collect(java.util.stream.Collectors.toList());
					title  = "Tomorrow's schedule (" + java.time.LocalDate.now().plusDays(1)
					.format(java.time.format.DateTimeFormatter.ofPattern("M/d")) + ")";
					break;
					case "week":
					case "this_week":
					events = calendarService.getThisWeek();
					title  = "This week's schedule";
					break;
					case "month":
					case "this_month":
					events = calendarService.getThisMonth();
					title  = "This month's schedule";
					break;
					case "nextmonth":
					case "next_month":
					events = calendarService.getNextMonth();
					title  = "Next month's schedule";
					break;
					default:
					try {
						int days = Integer.parseInt(arg);
						events = calendarService.getNextDays(days);
						title  = "Next " + days + " days schedule";
						} catch (NumberFormatException ex) {
						sendTelegram( "❓ Usage: /ms [today|tomorrow|week|month|N]");
						return;
					}
				}
				sendTelegram( GoogleCalendarService.formatEvents(title, events));
				} catch (Exception e) {
				sendTelegram( "❌ Schedule query failed: " + e.getMessage());
			}
		}, "ScheduleFetch").start();
	}
	
	/** /naverSchedule 명령 처리 - 인라인 버튼 메뉴 또는 직접 조회 */
	private void processNaverSchedule(String chatId, String text) {
		if (naverCalendarService == null || !naverCalendarService.isInitialized()) {
			System.out.println("[TelegramBot] Naver Calendar integration is not configured ");
			sendTelegram( "❌ Naver Calendar is not configured.\n"
				+ "Add the following to clock_settings.ini:\n\n"
				+ "  naver.caldav.id       = naver_id\n"
			+ "  naver.caldav.password = app_password");
			return;
		}
		String[] parts = text.trim().split("\\s+");
		if (parts.length == 1) {
			// 인라인 버튼 메뉴 표시
			sendWithInlineKeyboard(chatId,
				"📅 Select Naver schedule query range:",
				new String[][]{
					{"Today",     "ns_today"},
					{"Tomorrow",     "ns_tomorrow"},
					{"Next 3 days", "ns_3"},
					{"Next 7 days", "ns_7"},
					{"Past 7 days", "ns_week"},
					{"This month",  "ns_month"},
					{"Next month",  "ns_nextmonth"}
				});
				return;
		}
		fetchAndSendNaverSchedule(chatId, parts[1].toLowerCase());
	}
	
	/** Naver schedule 조회 및 전송 */
	private void fetchAndSendNaverSchedule(String chatId, String arg) {
		System.out.println("[TelegramBot] fetchAndSendNaverSchedule ");
		
		new Thread(() -> {
			try {
				java.util.List<NaverCalendarService.CalendarEvent> events;
				String title;
				switch (arg) {
					case "today":
					case "Today":
					events = naverCalendarService.getToday();
					title  = "Naver today's schedule (" + java.time.LocalDate.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("M/d")) + ")";
					break;
					case "tomorrow":
					case "Tomorrow":
					events = naverCalendarService.getNextDays(2).stream()
					.filter(e -> e.startTime.toLocalDate()
					.equals(java.time.LocalDate.now().plusDays(1)))
					.collect(java.util.stream.Collectors.toList());
					title  = "Naver tomorrow's schedule (" + java.time.LocalDate.now().plusDays(1)
					.format(java.time.format.DateTimeFormatter.ofPattern("M/d")) + ")";
					break;
					case "week":
					case "this_week":
					events = naverCalendarService.getThisWeek();
					title  = "Naver this week's schedule";
					break;
					case "month":
					case "this_month":
					events = naverCalendarService.getThisMonth();
					title  = "Naver this month's schedule";
					break;
					case "nextmonth":
					case "next_month":
					events = naverCalendarService.getNextMonth();
					title  = "Naver next month's schedule";
					break;
					default:
					try {
						int days = Integer.parseInt(arg);
						events = naverCalendarService.getNextDays(days);
						title  = "Naver next " + days + " days schedule";
						} catch (NumberFormatException ex) {
						sendTelegram( "❓ Usage: /ns [today|tomorrow|week|month|N]");
						return;
					}
				}
				sendTelegram( NaverCalendarService.formatEvents(title, events));
				} catch (Exception e) {
				sendTelegram( "❌ Naver schedule query failed: " + e.getMessage());
			}
		}, "NaverScheduleFetch").start();
	}
	

	private void sendOrRefreshMainMenu(String chatId) {
		String text = "🧭 Main Menu\n\nChoose a category.";
		String[][] buttons = new String[][]{
			{"🖥 System",   "menu_system"},
			{"📸 Capture",  "menu_capture"},
			{"📷 Camera",   "menu_camera"},
			{"📅 Schedule", "menu_schedule"},
			{"ℹ️ Help",     "menu_help"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showSystemMenu(String chatId) {
		String text = "🖥 System Menu\n\nSelect an action.";
		String[][] buttons = new String[][]{
			{"/wh", "menu_run_wh"},
			{"Help", "menu_help"},
			{"Shutdown", "menu_run_down"},
			{"Reboot", "menu_run_reboot"},
			{"⬅ Back", "menu_main"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showCaptureMenu(String chatId) {
		String text = "📸 Capture Menu\n\nSelect a screen capture.";
		String[][] buttons = new String[][]{
			{"Full Screen", "menu_run_s"},
			{"Clock",       "menu_run_c"},
			{"Monitor 1",   "menu_run_c1"},
			{"Monitor 2",   "menu_run_c2"},
			{"Monitor 3",   "menu_run_c3"},
			{"Monitor 4",   "menu_run_c4"},
			{"⬅ Back",      "menu_main"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showCameraMenu(String chatId) {
		String text = "📷 Camera Menu\n\n카메라 제어 명령을 선택하세요.";
		String[][] buttons = new String[][]{
			{"🔴 Start Loop (/cam)",        "cam_snapshot"},
			{"📷 Photo (/camHello)",        "cam_hello"},
			{"⏹ Stop Sending (/recstop)",  "cam_recstop"},
			{"🛑 Stop Camera (/camBye)",    "cam_bye"},
			{"⬅ Back",                      "menu_main"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 1);
	}

	private void showScheduleMenu(String chatId) {
		String text = "📅 Schedule Menu\n\nChoose a calendar action.";
		String[][] buttons = new String[][]{
			{"Google", "menu_google_schedule"},
			{"Naver",  "menu_naver_schedule"},
			{"⬅ Back", "menu_main"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showGoogleScheduleMenu(String chatId) {
		String text = "📅 Google Schedule\n\nSelect a range.";
		String[][] buttons = new String[][]{
			{"Today", "ms_today"},
			{"Tomorrow", "ms_tomorrow"},
			{"Next 3 days", "ms_3"},
			{"Next 7 days", "ms_7"},
			{"This month", "ms_month"},
			{"Next month", "ms_nextmonth"},
			{"⬅ Back", "menu_schedule"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showNaverScheduleMenu(String chatId) {
		String text = "📅 Naver Schedule\n\nSelect a range.";
		String[][] buttons = new String[][]{
			{"Today", "ns_today"},
			{"Tomorrow", "ns_tomorrow"},
			{"Next 3 days", "ns_3"},
			{"Next 7 days", "ns_7"},
			{"This month", "ns_month"},
			{"Next month", "ns_nextmonth"},
			{"⬅ Back", "menu_schedule"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void showMenuHelp(String chatId) {
		String text = "ℹ️ Menu Help\n\nThis menu stays in one message.\nAction results are sent as separate messages.";
		String[][] buttons = new String[][]{
			{"Command List", "menu_run_help"},
			{"Main Menu",    "menu_main"}
		};
		sendOrEditMenuMessage(chatId, text, buttons, 2);
	}

	private void sendOrEditMenuMessage(String chatId, String text, String[][] buttons, int perRow) {
		Integer messageId = menuMessageIds.get(chatId);
		if (messageId == null || !editInlineKeyboardMessage(chatId, messageId.intValue(), text, buttons, perRow)) {
			Integer newId = sendInlineKeyboardAndReturnMessageId(chatId, text, buttons, perRow);
			if (newId != null) menuMessageIds.put(chatId, newId);
		}
	}

	/** 인라인 버튼 클릭 콜백 처리 */
	private void processCallbackData(String chatId, String data) {
		System.out.println("[Telegram Callback] " + chatId + " → " + data);
		switch (data) {
			case "menu_main":            sendOrRefreshMainMenu(chatId); break;
			case "menu_system":          showSystemMenu(chatId); break;
			case "menu_capture":         showCaptureMenu(chatId); break;
			case "menu_camera":          showCameraMenu(chatId); break;
			case "menu_schedule":        showScheduleMenu(chatId); break;
			case "menu_google_schedule": showGoogleScheduleMenu(chatId); break;
			case "menu_naver_schedule":  showNaverScheduleMenu(chatId); break;
			case "menu_help":            showMenuHelp(chatId); break;
			// ── 카메라 ───────────────────────────────────────────────
			case "cam_snapshot": processCommand(chatId, "/cam");      break;
			case "cam_hello":    processCommand(chatId, "/camhello"); break;
			case "cam_recstop":  processCommand(chatId, "/recstop");  break;
			case "cam_bye":      processCommand(chatId, "/cambye");   break;
			case "menu_run_wh":          processCommand(chatId, "/wh"); break;
			case "menu_run_help":        sendHelpMenu(chatId); break;
			case "menu_run_s":           processCommand(chatId, "/s"); break;
			case "menu_run_c":           processCommand(chatId, "/c"); break;
			case "menu_run_c1":          processCapture(chatId, 0); break;
			case "menu_run_c2":          processCapture(chatId, 1); break;
			case "menu_run_c3":          processCapture(chatId, 2); break;
			case "menu_run_c4":          processCapture(chatId, 3); break;
			case "menu_run_down":        processCommand(chatId, "/d"); break;
			case "menu_run_reboot":      processCommand(chatId, "/r"); break;
			// 구글 캘린더
			case "ms_today":    fetchAndSendSchedule(chatId, "today");    break;
			case "ms_tomorrow": fetchAndSendSchedule(chatId, "tomorrow"); break;
			case "ms_week":     fetchAndSendSchedule(chatId, "week");     break;
			case "ms_month":    fetchAndSendSchedule(chatId, "month");    break;
			case "ms_nextmonth":fetchAndSendSchedule(chatId, "nextmonth");break;
			case "ms_3":        fetchAndSendSchedule(chatId, "3");        break;
			case "ms_7":        fetchAndSendSchedule(chatId, "7");        break;
			// 네이버 캘린더
			case "ns_today":    fetchAndSendNaverSchedule(chatId, "today");    break;
			case "ns_tomorrow": fetchAndSendNaverSchedule(chatId, "tomorrow"); break;
			case "ns_week":     fetchAndSendNaverSchedule(chatId, "week");     break;
			case "ns_month":    fetchAndSendNaverSchedule(chatId, "month");    break;
			case "ns_nextmonth":fetchAndSendNaverSchedule(chatId, "nextmonth");break;
			case "ns_3":        fetchAndSendNaverSchedule(chatId, "3");        break;
			case "ns_7":        fetchAndSendNaverSchedule(chatId, "7");        break;
			// /h 버튼 콜백 – 버튼 클릭 h 해당 명령 즉h 실행
			case "help_wh":     processStart(chatId, "/wh");        break;
			case "help_s":      processCommand(chatId, "/s");        break;
			case "help_c":      processCommand(chatId, "/c");        break;
			case "help_c1":     processCapture(chatId, 0);           break;
			case "help_c2":     processCapture(chatId, 1);           break;
			case "help_c3":     processCapture(chatId, 2);           break;
			case "help_c4":     processCapture(chatId, 3);           break;
			case "help_d":      processCommand(chatId, "/d");        break;
			case "help_r":      processCommand(chatId, "/r");        break;
			case "help_ms":     processMySchedule(chatId, "/ms");    break;
			case "help_ns":     processNaverSchedule(chatId, "/ns"); break;
			case "help_logout": processLogoutCalendar(chatId);       break;
			case "help_cmd":    sendCmdHelp(chatId);                 break;
			case "help_powerShell":    sendPowerShellHelp(chatId);                 break;
			case "help_save":   sendSaveHelp(chatId);                break;
			case "help_text":   sendTextHelp(chatId);                break;
			case "help_app":    sendBrowserButton(chatId);             break;
			case "help_h":      sendHelpMenu(chatId);                break;
			default:
			System.out.println("[Telegram Callback] unknown callback: " + data);
		}
	}
	
	
	public void processCapture(String chatId, int monitor ) {
		processCapture( chatId,  monitor, true) ;
	}
	
	
	public void processCapture(String chatId, int monitor , boolean noMonitor) {
		
		// Monitor 개수 확인 (GraphicsEnvironment Enable)
		int monitorCount = java.awt.GraphicsEnvironment
		.getLocalGraphicsEnvironment()
		.getScreenDevices().length;
		
		if (monitor >= monitorCount) {
			if  (noMonitor) sendTelegram( "Monitor " + (monitor + 1) + " not found (connected: " + monitorCount + ")");
			return;  // 스레드 실행 전에 조기 Shutdown
		}
		
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final File[] result = new File[1];
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {
							try {
								result[0] = handler.captureMonitor(monitor);
								} catch (Exception e) {
								System.out.println("[Capture] " + e.getMessage());
							}
						}
					});
					if (result[0] != null) sendFile(chatId, result[0]);
					else sendTelegram( "Capture failed: no result");
					} catch (Exception e) {
					sendTelegram( "Capture failed: " + e.getMessage());
				}
			}
		}, "TelegramCapture");
		t.setDaemon(true);
		t.start();
	}  //  processCapture
	/*
		private void processTray (String chatId, String text) {
		new Thread(() -> {
		try {
		final boolean[] visible = new boolean[1];
		SwingUtilities.invokeAndWait(() -> visible[0] = handler.toggleTrayWindow());
		if (visible[0]) sendTelegram(chatId, "🪟 Clock window is now visible.");
		else            sendTelegram(chatId, "📥 Clock window minimized to tray.");
		} catch (Exception ex) {
		sendTelegram(chatId, "❌ Tray toggle failed: " + ex.getMessage());
		}
		}, "TrayToggle").start();
		}  //  processTray
	*/
	
	private void processYes(String chatId, String text) {
		// getAndSet("") : 읽기와 initialization를 원자적으로 처리 → 중복 실행 원천 차단
		String pending = pendingCmd.getAndSet("");
		if (pending.equals("/down")) {
			sendShutdownNotice(false);
			new Thread(() -> {
				try {
					Thread.sleep(2000);
					handler.shutdownPC();
					} catch (Exception ex) {
					sendTelegram( "❌ Shutdown failed: " + ex.getMessage());
				}
			}, "Shutdown").start();
			} else if (pending.equals("/reboot")) {
			sendShutdownNotice(true);
			new Thread(() -> {
				try {
					Thread.sleep(2000);
					handler.rebootPC();
					} catch (Exception ex) {
					sendTelegram( "❌ Reboot failed: " + ex.getMessage());
				}
			}, "Reboot").start();
			} else {
			sendTelegram( "❓ No pending command.");
		}
		return ;
	}   // processYes
	
	private void processStart(String chatId, String text) {
		new Thread(() -> {
			try {
				String now      = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				String pcName   = java.net.InetAddress.getLocalHost().getHostName();
				String userId   = System.getProperty("user.name");
				String osName   = System.getProperty("os.name") + " " + System.getProperty("os.version");
				String javaVer  = System.getProperty("java.version");
				String localIp  = java.net.InetAddress.getLocalHost().getHostAddress();
				publicIp = getPublicIp();
				String msg = "🖥 PC Info\n\n"
				+ "🕐 Time     : " + now      + "\n"
				+ "💻 PC Name  : " + pcName   + "\n"
				+ "👤 User     : " + userId   + "\n"
				+ "🌐 IP (LAN) : " + localIp  + "\n"
				+ "🌍 IP (WAN) : " + publicIp + "\n"
				+ "🖥 OS       : " + osName   + "\n"
				+ "☕ Java     : " + javaVer;
				sendTelegram( msg);
				} catch (Exception ex) {
				sendTelegram( "❌ PC info query failed: " + ex.getMessage());
			}
		}, "WhInfo").start();
	}  //  processStart
	
	private void processSave(String chatId, String text) {
		String saveArgs = text.substring("/save".length());
		int nl = saveArgs.indexOf('\n');
		if (nl < 0) {
			sendTelegram( "Usage: /save filename\nline 1\nline 2\n...");
			return ;
		}
		String fileName = saveArgs.substring(0, nl).trim();
		String content  = saveArgs.substring(nl + 1);
		if (fileName.isEmpty()) {
			sendTelegram( "No filename specified.");
			return ;
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			sendTelegram( "❌ Filename cannot contain path characters.");
			return ;
		}
		try {
			// java.io.File saveFile = new java.io.File(System.getProperty("user.dir"), fileName);
			java.io.File saveFile = new java.io.File(this.telegramPathS, fileName);
			if (saveFile.exists()) {
				sendTelegram( "❌ File already exists. Please use a different name: " + saveFile.getAbsolutePath());
				return ;
			}
			try (java.io.PrintWriter pw = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(saveFile), "UTF-8"))) {
				pw.print(content);
			}
			sendTelegram( "✅ Saved: " + saveFile.getAbsolutePath());
			} catch (Exception ex) {
			sendTelegram( "❌ Save failed: " + ex.getMessage());
		}
		return ;
	}	//  processSave
	
	private void processPowerShell(String chatId, String text) {
		String command = text.substring("/ps".length()).trim();
		if (command.isEmpty()) {
			sendTelegram(
				"Usage:\n" +
				"  /ps <command>\n" +
				"  /ps Get-Process\n" +
				"  /ps Get-Service\n" +
				"  /ps Get-ChildItem\n" +
				"  /ps Get-Date\n"
			);
			return;
		}
		// 기존 차단 로직 재Enable
		/*
			String blockedKeyword = findBlockedKeyword(command);
			if (blockedKeyword != null) {
			sendTelegram("🚫 [" + blockedKeyword + "] is not allowed.");
			AppLogger.writeToFile("[PS Block] chatId=" + chatId + " cmd=" + command);
			return;
			}
		*/
		final String finalCmd = command;
		new Thread(() -> {
			try {
				processCore(2, finalCmd);
			} catch (Exception ex) {
				sendTelegram("❌ Execution failed: " + ex.getMessage());
			}
		}, "CmdExec").start();
		return;
	}  //  processPowerShell
		/*
			new Thread(() -> {
			try {
			ProcessBuilder pb = new ProcessBuilder();
			pb.directory(this.telegramPathF);
			pb.command(
			"powershell.exe",
			"-NoProfile",
			"-Command",
			finalCmd
			);
			pb.redirectErrorStream(true);
			Process proc = pb.start();
			java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(proc.getInputStream(), "MS949")
			);
			StringBuilder fullSb = new StringBuilder();
			StringBuilder tgSb   = new StringBuilder();
			String line;
			int lineCount = 0, maxLines = 50;
			while ((line = br.readLine()) != null) {
			fullSb.append(line).append("\n");
			if (lineCount < maxLines) tgSb.append(line).append("\n");
			lineCount++;
			}
			proc.waitFor();
			boolean truncated = (lineCount > maxLines);
			if (truncated) {
			tgSb.append("\n. (").append(lineCount)
			.append(" of ").append(maxLines)
			.append(" lines shown)");
			}
			if (tgSb.length() > 0) sendTelegram("```\n" + tgSb + "```");
			else                   sendTelegram("✅ Execution complete (no output)");
			} catch (Exception ex) {
			sendTelegram("❌ PowerShell Execution failed: " + ex.getMessage());
			}
			}, "PsExec").start();
		*/
	private void processCMD(String chatId, String text) 	{
		String command = text.substring("/cmd".length()).trim();
		if (command.isEmpty()) {
			sendTelegram( "Usage:\n" +
				"  /cmd <command>\n" +
				"  /cmd dir\n" +
				"  /cmd ipconfig\n" +
				"  /cmd tasklist\n" +
				"  /cmd systeminfo\n" +
				"  /cmd myScript.bat\n\n"
			);
			return ;
		}
		// ── 위험 명령어 차단 ──────────────────────────────
		String blockedKeyword = findBlockedKeyword(command);
		if (blockedKeyword != null) {
			sendTelegram( "🚫 [" + blockedKeyword + "] is not allowed.");
			AppLogger.writeToFile("[CMD Block] chatId=" + chatId + " cmd=" + command);
			return ;
		}
		// ─────────────────────────────────────────────────
		final String finalCmd = command;
		new Thread(() -> {
			try {
				processCore(1, finalCmd);
			} catch (Exception ex) {
				sendTelegram("❌ Execution failed: " + ex.getMessage());
			}
		}, "CmdExec").start();
		return;
	}  //  processCMD
	private void processCore(int interpreter, String finalCmd) throws Exception {
		System.out.printf("[processCore] interpreter=%d, cmd=%s%n", interpreter, finalCmd);
		ProcessBuilder pb = new ProcessBuilder();
		pb.directory(this.telegramPathF); // 여기 기준으로 dir 실행
		if ( interpreter == 1) {		
			pb.command("cmd.exe", "/c", finalCmd);
			} else if ( interpreter == 2) {
			// Out-String -Width 80 : 출력 폭 고정 → Telegram 에서 lines 안 꺾임
			String psCmd = "(" + finalCmd + ") | Out-String -Width 120";
			pb.command("powershell.exe", "-NoProfile", "-Command", psCmd);
			} else {
			System.out.println("program error : CMD PS classification error");
			return;
		}
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		java.io.BufferedReader br = new java.io.BufferedReader(
		new java.io.InputStreamReader(proc.getInputStream(), "MS949"));
		StringBuilder fullSb = new StringBuilder(); // full result (버림 없이)
		StringBuilder tgSb   = new StringBuilder(); // Telegram용 (최대 50lines)
		String line;
		int lineCount = 0, maxLines = 50;
		while ((line = br.readLine()) != null) {
			fullSb.append(line).append("\n");
			if (lineCount < maxLines) tgSb.append(line).append("\n");
			lineCount++;
		}
		proc.waitFor();
		boolean truncated = (lineCount > maxLines);
		if (truncated)
		tgSb.append("\n... (").append(lineCount).append(" of ").append(maxLines).append(" lines shown)");
		// 텔레그램에 결과 전송 (50lines 이Other or 앞부분)
		if (tgSb.length() > 0) sendTelegram("```\n" + tgSb + "```");
		else                   sendTelegram("✅ Execution complete (no output)");
		
		// 잘린 경우 Gmail All 결과 전송
		if (truncated) {
			boolean gmailOk = false;
			GmailSender gmailSender = GmailSender.getInstance();
			gmailSender.init();
			if (gmailSender != null && gmailSender.isConfigured()) {
				String subject;
				if (interpreter == 1) {
					subject = "[KootPanKing] /cmd " + finalCmd + " full result";
				} else if (interpreter == 2) {
					subject = "[KootPanKing] /PowerShell " + finalCmd + " full result";
				} else {
					return;
				}
				try {
					gmailSender.sendOneSmtp(gmailSender.lastTo, subject, fullSb.toString());
					gmailOk = true;
					} catch (Exception mailEx) {
					System.out.println("[CMD] Gmail send Failed: " + mailEx.getMessage());
				}
			}
			if (gmailOk)
			sendTelegram("⚠️ Result has " + lineCount + " lines. " + maxLines + " lines shown.\n"
			+ "📧 Full result sent via Gmail → " + gmailSender.lastTo);
			else
			sendTelegram("⚠️ Result has " + lineCount + " lines. " + maxLines + " lines shown.\n"
			+ "📧 Gmail not configured — full result not sent.");
		}
	}
	
	// ── 메시지 전송 ───────────────────────────────────────────────
	/** Text Message 전송 */
	public void sendTelegram(String text) {
		System.out.println("[TelegramBot] sendTelegram(String chatId, String text) ");
		if (this.botToken.isEmpty() ) {
			System.out.println("[Telegram] Bot Token missing");
			return;
		}
		if (this.myChatId.isEmpty()) {
			System.out.println("[Telegram] Chat ID missing");
			return;
		}
		try {
			URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			String body = "chat_id=" + java.net.URLEncoder.encode(this.myChatId, "UTF-8")
			+ "&text="    + java.net.URLEncoder.encode(text,   "UTF-8");
			con.getOutputStream().write(body.getBytes("UTF-8"));
			int code = con.getResponseCode();
			con.disconnect();
			System.out.println("[Telegram] Sending Done code=" + code);
			} catch (Exception e) {
			System.out.println("[Telegram] Sending error: " + e.getMessage());
		}
		// 카카오톡 동h 전송 (로그인된 경우에만)
		if (kakao != null && !kakao.kakaoAccessToken.isEmpty()) {
			final String kakaoText = text;
			new Thread(() -> kakao.sendKakao("", kakaoText), "KakaoMirror").start();
		}
	}
	
	public void sendTelegramWarning(String chatId ,String text) {
		System.out.println("[TelegramBot] sendTelegram(String chatId, String text) ");
		if (botToken.isEmpty() ) {
			System.out.println("[Telegram] Bot Token missing");
			return;
		}
		if (chatId.isEmpty()) {
			System.out.println("[Telegram] Chat ID missing");
			return;
		}
		try {
			URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			String body = "chat_id=" + java.net.URLEncoder.encode(chatId, "UTF-8")
			+ "&text="    + java.net.URLEncoder.encode(text,   "UTF-8");
			con.getOutputStream().write(body.getBytes("UTF-8"));
			int code = con.getResponseCode();
			con.disconnect();
			System.out.println("[Telegram] Sending Done code=" + code);
			} catch (Exception e) {
			System.out.println("[Telegram] Sending error: " + e.getMessage());
		}
	}
	
	/**
		* 인라인 키보드 버튼이 포함된 메시지 전송.
		* buttons: [ ["버튼Text1", "callbackData1"], ["버튼Text2", "callbackData2"] ... ]
		* 한 lines에 2개씩 배치됨
	*/
	public void sendWithInlineKeyboard(String chatId, String text, String[][] buttons) {
		if (botToken.isEmpty() || chatId.isEmpty()) return;
		try {
			StringBuilder kb = new StringBuilder("[");
			for (int i = 0; i < buttons.length; i += 2) {
				if (i > 0) kb.append(",");
				kb.append("[");
				kb.append("{\"text\":\"").append(buttons[i][0])
				.append("\",\"callback_data\":\"").append(buttons[i][1]).append("\"}");
				if (i + 1 < buttons.length) {
					kb.append(",{\"text\":\"").append(buttons[i+1][0])
					.append("\",\"callback_data\":\"").append(buttons[i+1][1]).append("\"}");
				}
				kb.append("]");
			}
			kb.append("]");
			
			String jsonBody = "{\"chat_id\":\"" + chatId + "\","
			+ "\"text\":\"" + text.replace("\"","\\\"").replace("\n","\\n") + "\","
			+ "\"reply_markup\":{\"inline_keyboard\":" + kb + "}}";
			
			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));
			int code = con.getResponseCode();
			con.disconnect();
			System.out.println("[Telegram] inline keyboard send code=" + code);
			} catch (Exception e) {
			System.out.println("[Telegram] inline keyboard Sending error: " + e.getMessage());
		}
	}
	
	/** /h, /help 명령: 인라인 버튼 list 전송 (한 lines 1) */
	/** /ps Enable법 Info */
	private void sendPowerShellHelp(String chatId) {
		sendTelegram(
			"💻 /ps Usage\n\n" +
			"/ps <PowerShell command>\n\n" +
			"Yes)\n" +
			"  /ps Get-Process\n" +
			"  /ps Get-Service\n" +
			"  /ps Get-ChildItem C:\\\\\n" +
			"  /ps Get-Date\n" +
			"  /ps Get-EventLog -LogName System -Newest 10\n\n" +
			"※ Executes a PowerShell command on your PC and sends the result.\n"
		);
	}

	private void sendTextHelp(String chatId) {
		sendTelegram(
			"📝 /text Usage\n\n" +
			"/text <text to save>\n\n" +
			"Yes)\n" +
			"  /text Today's task list\n" +
			"  /text Enter your memo here\n\n" +
			"※ Saved as Telegram_datetime.txt in TELEGRAM folder\n" +
			"   and automatically opened in the main window tab.\n"
		);
	}
	
	private void sendCmdHelp(String chatId) {
		sendTelegram(
			"💻 /cmd Usage\n\n" +
			"/cmd <command>\n" +
			"/cmd <filename.bat>\n\n" +
			"Yes)\n" +
			"  /cmd dir\n" +
			"  /cmd ipconfig\n" +
			"  /cmd tasklist\n" +
			"  /cmd systeminfo\n" +
			"  /cmd myScript.bat\n\n" +
		"※ Executes a command or batch file on your PC and sends the result.\n");
	}
	
	/** /save Enable법 Info */
	private void sendSaveHelp(String chatId) {
		sendTelegram(
			"💾 /save Usage\n\n" +
			"/save <filename>\n" +
			"first line\n" +
			"second line\n" +
			"...\n\n" +
			"Example - create a batch file)\n" +
			"  /save hello.bat\n" +
			"  @echo off\n" +
			"  echo Hello World!\n" +
			"  pause\n\n" +
			"※ Files are saved in the TELEGRAM folder.\n" +
		"※ .bat files created with /save can be run directly with /cmd.");
	}
	
	/** /h — 인라인 버튼 도움말 (2열 격자 = 빨래판 모양) */
	private void sendHelpMenu(String chatId) {
		sendWithInlineKeyboard(chatId,
			"📋 Command List  (tap to execute)",
			new String[][]{
				{"📸 /c  - Clock capture",            "help_c"},
				{"🖥 /s  - Full screen capture",       "help_s"},
				{"/c1 - Monitor 1",                   "help_c1"},
				{"/c2 - Monitor 2",                   "help_c2"},
				{"/c3 - Monitor 3",                   "help_c3"},
				{"/c4 - Monitor 4",                   "help_c4"},
				{"🔴 /cam - 10초 영상 루프",          "cam_snapshot"},
				{"📷 /camHello - 사진 1장 (자동연결)", "cam_hello"},
				{"⏹ /recstop - Stop sending",         "cam_recstop"},
				{"🛑 /camBye - Stop camera",           "cam_bye"},
				{"💀 /d  - Shutdown PC",               "help_d"},
				{"🔄 /r  - Reboot PC",                "help_r"},
				{"📅 /ms - Google Calendar",           "help_ms"},
				{"📅 /ns - Naver Calendar",            "help_ns"},
				{"/cmd ... DOS command",               "help_cmd"},
				{"/ps  ... PowerShell",                "help_powerShell"},
				{"/save ... create .BAT file",         "help_save"},
				{"/text ... send & save text",         "help_text"},
				{"/app ... Control Panel",             "help_app"},
				{"/help - Text help list",             "help_h"},
			},
		2);
	}

	/** /help — 텍스트 도움말 전체 전송 */
	private void sendHelpText(String chatId) {
		String text =
			"📋 전체 명령어 목록\n" +
			"━━━━━━━━━━━━━━━━━━━━\n" +
			"📸 화면 캡처\n" +
			"/c  — 시계 화면 캡처\n" +
			"/s  — 전체 화면 캡처\n" +
			"/c1 ~ /c4  — 모니터 1~4 캡처\n\n" +
			"📷 카메라\n" +
			"/cam       — 10초마다 동영상 전송 (연속 루프 시작)\n" +
			"/camHello  — 사진 1장 촬영·전송 (카메라 미연결 시 자동 연결)\n" +
			"/recstop   — 전송 중단 (카메라 계속 촬영)\n" +
			"/camBye    — 현재 클립 저장·전송 후 카메라 종료\n\n" +
			"🖥 시스템\n" +
			"/wh   — PC 정보\n" +
			"/d    — PC 종료\n" +
			"/r    — PC 재시작\n" +
			"/cmd [명령] — DOS 명령 실행\n" +
			"/ps  [명령] — PowerShell 명령 실행\n\n" +
			"📅 캘린더\n" +
			"/ms  — Google 캘린더 (3일)\n" +
			"/ns  — Naver 캘린더\n\n" +
			"⚙️ 기타\n" +
			"/save [내용] — .BAT 파일 생성\n" +
			"/text [내용] — 텍스트 저장·전송\n" +
			"/app  — 제어판 열기\n" +
			"/menu — 인라인 메뉴\n" +
			"/h    — 도움말 (인라인 버튼)\n" +
			"/help — 도움말 (텍스트)";
		sendTelegramWarning(chatId, text);
	}
	
	/**
		* 인라인 키보드 전송 – perRow 지정 Version.
		* perRow=1 이면 한 lines에 버튼 1개, perRow=2 이면 2개씩.
	*/
	public void sendWithInlineKeyboard(String chatId, String text, String[][] buttons, int perRow) {
		if (botToken.isEmpty() || chatId.isEmpty()) return;
		try {
			StringBuilder kb = new StringBuilder("[");
			for (int i = 0; i < buttons.length; i += perRow) {
				if (i > 0) kb.append(",");
				kb.append("[");
				for (int j = 0; j < perRow && i + j < buttons.length; j++) {
					if (j > 0) kb.append(",");
					kb.append("{\"text\":\"").append(escapeJson(buttons[i + j][0]))
					.append("\",\"callback_data\":\"").append(buttons[i + j][1]).append("\"}");
				}
				kb.append("]");
			}
			kb.append("]");
			String jsonBody = "{\"chat_id\":\"" + chatId + "\","
			+ "\"text\":\"" + escapeJson(text) + "\","
			+ "\"reply_markup\":{\"inline_keyboard\":" + kb + "}}";
			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));
			int code = con.getResponseCode();
			con.disconnect();
			System.out.println("[Telegram] inline keyboard send code=" + code);
			} catch (Exception e) {
			System.out.println("[Telegram] inline keyboard Sending error: " + e.getMessage());
		}
	}

	private String buildInlineKeyboardJson(String[][] buttons, int perRow) {
		StringBuilder kb = new StringBuilder("[");
		for (int i = 0; i < buttons.length; i += perRow) {
			if (i > 0) kb.append(",");
			kb.append("[");
			for (int j = 0; j < perRow && i + j < buttons.length; j++) {
				if (j > 0) kb.append(",");
				kb.append("{\"text\":\"").append(escapeJson(buttons[i + j][0]))
				  .append("\",\"callback_data\":\"").append(buttons[i + j][1]).append("\"}");
			}
			kb.append("]");
		}
		kb.append("]");
		return kb.toString();
	}

	private Integer sendInlineKeyboardAndReturnMessageId(String chatId, String text, String[][] buttons, int perRow) {
		if (botToken.isEmpty() || chatId == null || chatId.isEmpty()) return null;
		try {
			String kb = buildInlineKeyboardJson(buttons, perRow);
			String jsonBody = "{\"chat_id\":\"" + chatId + "\","
				+ "\"text\":\"" + escapeJson(text) + "\","
				+ "\"reply_markup\":{\"inline_keyboard\":" + kb + "}}";

			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));

			int code = con.getResponseCode();
			java.io.InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
			String response = readStreamAsString(is);
			con.disconnect();
			if (code < 200 || code >= 300) {
				System.out.println("[Telegram] send menu failed code=" + code + " body=" + response);
				return null;
			}
			try {
				JSONObject root = (JSONObject) new JSONParser().parse(response);
				JSONObject result = (JSONObject) root.get("result");
				if (result == null) return null;
				Object messageIdObj = result.get("message_id");
				if (messageIdObj instanceof Long) return Integer.valueOf(((Long) messageIdObj).intValue());
				if (messageIdObj instanceof Number) return Integer.valueOf(((Number) messageIdObj).intValue());
			} catch (Exception parseEx) {
				System.out.println("[Telegram] send menu parse error: " + parseEx.getMessage());
			}
		} catch (Exception e) {
			System.out.println("[Telegram] send menu error: " + e.getMessage());
		}
		return null;
	}

	private boolean editInlineKeyboardMessage(String chatId, int messageId, String text, String[][] buttons, int perRow) {
		if (botToken.isEmpty() || chatId == null || chatId.isEmpty() || messageId <= 0) return false;
		try {
			String kb = buildInlineKeyboardJson(buttons, perRow);
			String jsonBody = "{\"chat_id\":\"" + chatId + "\","
				+ "\"message_id\":" + messageId + ","
				+ "\"text\":\"" + escapeJson(text) + "\","
				+ "\"reply_markup\":{\"inline_keyboard\":" + kb + "}}";
			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/editMessageText");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));
			int code = con.getResponseCode();
			String response = readStreamAsString((code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream());
			con.disconnect();
			if (code >= 200 && code < 300) return true;
			System.out.println("[Telegram] edit menu failed code=" + code + " body=" + response);
		} catch (Exception e) {
			System.out.println("[Telegram] edit menu error: " + e.getMessage());
		}
		return false;
	}

	private String readStreamAsString(java.io.InputStream is) throws java.io.IOException {
		if (is == null) return "";
		java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
		return baos.toString("UTF-8");
	}
	

	private void sendPreviewHtmlSource(String chatId) {
		try {
			File previewFile = new File("C:\\temp\\TELEGRAM_preview.html");
			if (!previewFile.exists() || !previewFile.isFile()) {
				sendTelegram("⚠ Preview HTML not found: " + previewFile.getAbsolutePath());
				return;
			}

			String html = new String(
				java.nio.file.Files.readAllBytes(previewFile.toPath()),
				java.nio.charset.StandardCharsets.UTF_8
			);

			if (html.trim().isEmpty()) {
				sendTelegram("⚠ Preview HTML is empty.");
				return;
			}

			int maxLen = 3500;
			if (html.length() <= maxLen) {
				sendTelegram("📄 TELEGRAM_preview.html\n\n" + html);
			} else {
				int part = 1;
				for (int start = 0; start < html.length(); start += maxLen) {
					int end = Math.min(start + maxLen, html.length());
					String chunk = html.substring(start, end);
					sendTelegram("📄 TELEGRAM_preview.html (part " + part + ")\n\n" + chunk);
					part++;
				}
			}
		} catch (Exception e) {
			sendTelegram("❌ Failed to read preview HTML: " + e.getMessage());
		}
	}

	
	public void sendBrowserButton(String chatId) {
		if (botToken.isEmpty() || chatId == null || chatId.isEmpty()) return;

		try {
			String pageUrl = AppContext.get("tg.webAppUrl", "").trim();
			if (pageUrl.isEmpty()) {
				sendTelegram("⚠ tg.webAppUrl is empty.");
				return;
			}

			String jsonBody =
				"{"
				+ "\"chat_id\":\"" + escapeJson(chatId) + "\","
				+ "\"text\":\"🧭 Control Panel을 여세요\","
				+ "\"reply_markup\":{"
					+ "\"inline_keyboard\":["
						+ "[{"
							+ "\"text\":\"🌐 Open Control Panel\","
							+ "\"url\":\"" + escapeJson(pageUrl) + "\""
						+ "}]"
					+ "]"
				+ "}"
				+ "}";

			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));

			int code = con.getResponseCode();
			con.disconnect();

			System.out.println("[Telegram] browser button send code=" + code);
		} catch (Exception e) {
			System.out.println("[Telegram] browser button send error: " + e.getMessage());
		}
	}


	/** JSON 문자열 이스케이프 헬퍼 */
	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
	}
	
	/** 콜백 쿼리에 응답 (버튼 클릭 후 로딩 스피너 제거) */
	private void answerCallbackQuery(String callbackQueryId) {
		if (botToken.isEmpty() || callbackQueryId.isEmpty()) return;
		try {
			String jsonBody = "{\"callback_query_id\":\"" + callbackQueryId + "\"}";
			java.net.URL url = toUrl("https://api.telegram.org/bot" + botToken + "/answerCallbackQuery");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.getOutputStream().write(jsonBody.getBytes("UTF-8"));
			con.getResponseCode();
			con.disconnect();
			} catch (Exception e) {
			System.out.println("[Telegram] answerCallbackQuery error: " + e.getMessage());
		}
	}
	
	/** File(Images/Document) 전송 */
	public void sendFile(String chatId, File file) throws Exception {
		String name    = file.getName().toLowerCase();
		boolean isImg  = name.endsWith(".jpg") || name.endsWith(".jpeg")
		|| name.endsWith(".png") || name.endsWith(".gif")
		|| name.endsWith(".bmp") || name.endsWith(".webp");
		String method  = isImg ? "sendPhoto"    : "sendDocument";
		String field   = isImg ? "photo"        : "document";
		String boundary = "----TelegramBoundary" + System.currentTimeMillis();
		
		URL url = toUrl("https://api.telegram.org/bot" + botToken + "/" + method);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		con.setConnectTimeout(30000);
		con.setReadTimeout(60000);
		con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		
		try (java.io.OutputStream out = con.getOutputStream()) {
			// chat_id File트
			out.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n"
			+ chatId + "\r\n").getBytes("UTF-8"));
			// File File트 헤더
			out.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"" + field
				+ "\"; filename=\"" + file.getName() + "\"\r\n"
			+ "Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
			// File 내용
			try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
				byte[] buf = new byte[8192];
				int len;
				while ((len = fis.read(buf)) != -1) out.write(buf, 0, len);
			}
			out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
		}
		int code = con.getResponseCode();
		con.disconnect();
		if (code != 200) throw new Exception("HTTP " + code);
	}

	/**
	 * mp4 파일을 sendVideo API 로 전송 — 텔레그램에서 자동재생됨.
	 * supports_streaming=true + +faststart 인코딩 시 인라인 자동재생.
	 */
	public void sendVideo(String chatId, File file) throws Exception {
		String boundary = "----TelegramBoundary" + System.currentTimeMillis();
		URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendVideo");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		con.setConnectTimeout(30000);
		con.setReadTimeout(120000); // 대용량 업로드 대비
		con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		try (java.io.OutputStream out = con.getOutputStream()) {
			// chat_id
			out.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n"
				+ chatId + "\r\n").getBytes("UTF-8"));
			// supports_streaming
			out.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"supports_streaming\"\r\n\r\n"
				+ "true\r\n").getBytes("UTF-8"));
			// video 파일
			out.write(("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"video\""
				+ "; filename=\"" + file.getName() + "\"\r\n"
				+ "Content-Type: video/mp4\r\n\r\n").getBytes("UTF-8"));
			try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
				byte[] buf = new byte[8192]; int len;
				while ((len = fis.read(buf)) != -1) out.write(buf, 0, len);
			}
			out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
		}
		int code = con.getResponseCode();
		con.disconnect();
		System.out.println("[TgSendVideo] " + file.getName() + " → HTTP " + code);
		if (code != 200) throw new Exception("sendVideo HTTP " + code);
	}

	// ── 첨부File 수신 Save ─────────────────────────────────────────
	/**
		* 스마트폰에서 보낸 photo / document / audio / video / voice 를
		* 현재Folder/download/ 에 Save한다.
	*/
	/** json-simple File싱된 message JSONObject 에서 Attachments 처리 */
	private void receiveFileFromJson(String chatId, JSONObject message) {
		String fileId = null;
		String savedName = null;
		// photo: 배열 중 마지막(최고해상도)
		JSONArray photo = (JSONArray) message.get("photo");
		if (photo != null && !photo.isEmpty()) {
			JSONObject largest = (JSONObject) photo.get(photo.size() - 1);
			fileId = (String) largest.get("file_id");
			savedName = "photo_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".jpg";
		}
		String[] types = {"document", "video", "audio", "voice", "animation"};
		for (String t : types) {
			if (fileId != null) break;
			JSONObject obj = (JSONObject) message.get(t);
			if (obj == null) continue;
			fileId    = (String) obj.get("file_id");
			savedName = (String) obj.get("file_name");
			if (savedName == null || savedName.isEmpty()) {
				java.util.Map<String,String> ext = new java.util.HashMap<>();
				ext.put("video",".mp4"); ext.put("audio",".mp3");
				ext.put("voice",".ogg"); ext.put("animation",".gif"); ext.put("document","");
				savedName = t + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ext.getOrDefault(t,"");
				} else {
				savedName = sanitizeFileName(savedName);
			}
		}
		if (fileId == null || savedName == null) { System.out.println("[receiveFile] unsupported File type"); return; }
		// 기존 receiveFile 의 Download 로직 재Enable
		String dummyBlock = "\"document\":{\"file_id\":\"" + fileId + "\",\"file_name\":\"" + savedName + "\"},";
		receiveFile(chatId, dummyBlock);
	}
	
	/** /text 바디를 그대로 Save (json-simple 이 이미 모든 이스케이프 처리) */
	private void saveBodyDirectly(String chatId, String body) {
		if (body.isEmpty()) { sendTelegram("❌ /text: No content."); return; }
		if (telegramPathF == null) {
			String dir = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + java.io.File.separator + "TELEGRAM";
			telegramPathF = new java.io.File(dir);
			if (!telegramPathF.exists()) telegramPathF.mkdirs();
		}
		try {
			String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
			java.io.File outFile = new java.io.File(telegramPathF, "Telegram_" + ts + ".txt");
			try (java.io.Writer w = new java.io.OutputStreamWriter(
			new java.io.FileOutputStream(outFile), java.nio.charset.StandardCharsets.UTF_8)) {
			w.write(body);
			}
			System.out.println("[saveBodyDirectly] Save: " + outFile.getAbsolutePath());
			sendTelegram("✅ Saved: " + outFile.getName());
			final java.io.File ff = outFile;
			javafx.application.Platform.runLater(() -> handler.openTextFile(ff));
			} catch (Exception e) {
			sendTelegram("❌ Save failed: " + e.getMessage());
			System.out.println("[saveBodyDirectly] " + e.getMessage());
		}
	}
	
	private void receiveFile(String chatId, String block) {
		try {
			// ── file_id 추출: 우선순위 photo > document > video > audio > voice > animation
			String fileId = null;
			String savedName = null;
			
			// photo: 배열 중 가장 큰 해상도 (마지막 file_id)
			Pattern photoIdPat = Pattern.compile("\"file_id\":\"([^\"]+)\"");
			if (block.contains("\"photo\"")) {
				Matcher m = photoIdPat.matcher(block);
				while (m.find()) fileId = m.group(1); // 마지막 = 최고해상도
				if (fileId != null) {
					// 타임스탬프 File명
					savedName = "photo_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
					.format(new java.util.Date()) + ".jpg";
				}
			}
			
			// document / video / audio / voice / animation
			if (fileId == null) {
				String[] types = {"document", "video", "audio", "voice", "animation"};
				for (String t : types) {
					if (!block.contains("\"" + t + "\"")) continue;
					// 해당 타입 블록 먼저 추출 (file_name을 typeBlock 안에서만 검색)
					int ti = block.indexOf("\"" + t + "\"");
					String typeBlock = block.substring(ti);
					// file_name 추출
					Pattern fnPat = Pattern.compile("\"file_name\":\"([^\"]+)\"");
					Matcher fnMat = fnPat.matcher(typeBlock);
					if (fnMat.find()) {
						savedName = sanitizeFileName(fnMat.group(1));
						} else {
						// file_name 없을 때 타입별 기본 확장자 부여 (없으면 isMediaFile() 판별 Failed)
						java.util.Map<String, String> defaultExt = new java.util.HashMap<>();
						defaultExt.put("video",     ".mp4");
						defaultExt.put("audio",     ".mp3");
						defaultExt.put("voice",     ".ogg");
						defaultExt.put("animation", ".gif");
						defaultExt.put("document",  "");
						String defExt = defaultExt.getOrDefault(t, "");
						savedName = t + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
						.format(new java.util.Date()) + defExt;
					}
					// file_id: 해당 타입 객체 All에서 마지막 file_id Enable
					// 텔레그램 video JSON 구조: thumbnail{file_id}, thumb{file_id}, file_id(본체)
					// → thumbnail/thumb 의 file_id 가 먼저, 본체 file_id 가 마지막에 위치
					Matcher idMat = photoIdPat.matcher(typeBlock);
					while (idMat.find()) fileId = idMat.group(1); // 마지막 = 본체
					break;
				}
			}
			
			if (fileId == null || savedName == null) {
				System.out.println("[Telegram] attachment file_id extract failed");
				return;
			}
			
			// ── getFile API → file_path + file_size 획득 (가장 정확한 크기 출처)
			String[] fileInfo = getFileInfo(fileId);
			if (fileInfo == null) {
				sendTelegram( "❌ File path lookup failed");
				return;
			}
			String filePath = fileInfo[0];
			long   fileSize = Long.parseLong(fileInfo[1]); // -1이면 Size 미제공
			
			// ── File 크기 체크
			// Image/미디어: 50MB, 반 File: 20MB (텔레그램 Bot API 실제 상한 50MB)
			boolean isMediaOrImage = isImageFile(new java.io.File(savedName))
			|| isMediaFile(new java.io.File(savedName));
			long   limitBytes = isMediaOrImage ? 50L * 1024 * 1024 : 20L * 1024 * 1024;
			String limitLabel = isMediaOrImage ? "50MB" : "20MB";
			
			// path==null 이면 텔레그램이 getFile 자체를 거부 = 50MB 초과 확실
			if (filePath == null || fileSize < 0) {
				System.out.println("[Telegram] file unavailable -> exceeds 50MB");
				sendTelegram( "❌ File is too large to save.\n"
					+ "⚠️ Max allowed: " + limitLabel + "\n"
				+ "(Telegram Bot API 50MB limit)");
				return;
			}
			if (fileSize > limitBytes) {
				String sizeMB = String.format("%.1f", fileSize / 1024.0 / 1024.0);
				System.out.println("[Telegram] file size exceeded: " + sizeMB + "MB -> receive rejected");
				sendTelegram( "❌ File too large to save.\n"
					+ "📦 File Size: " + sizeMB + "MB\n"
				+ "⚠️ Max allowed: " + limitLabel);
				return;
			}
			
			// ── download Folder 생성
			// java.io.File dlDir = new java.io.File(System.getProperty("user.dir"), "download");
			// java.io.File dlDir = new java.io.File(resolveRunDir(), "download");
			
			String dlDirS = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + File.separator + "DOWNLOAD";			
			java.io.File dlDir = new java.io.File(dlDirS);
			
			if (!dlDir.exists()) dlDir.mkdirs();
			
			// ── 중복 File명 처리
			java.io.File outFile = new java.io.File(dlDir, savedName);
			if (outFile.exists()) {
				String base = savedName.contains(".")
				? savedName.substring(0, savedName.lastIndexOf('.'))
				: savedName;
				String ext  = savedName.contains(".")
				? savedName.substring(savedName.lastIndexOf('.'))
				: "";
				int seq = 1;
				while (outFile.exists()) {
					outFile = new java.io.File(dlDir, base + "(" + seq++ + ")" + ext);
				}
			}
			
			// ── 실제 Download
			String downloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
			HttpURLConnection con = (HttpURLConnection) toUrl(downloadUrl).openConnection();
			con.setConnectTimeout(30000);
			con.setReadTimeout(60000);
			int dlCode = con.getResponseCode();
			if (dlCode != 200) {
				con.disconnect();
				System.out.println("[Telegram] Download Failed HTTP " + dlCode);
				sendTelegram( "❌ File Download failed (HTTP " + dlCode + ")\n"
				+ "File may be too large or expired.");
				return;
			}
			try (java.io.InputStream in  = con.getInputStream();
				java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
				byte[] buf = new byte[8192];
				int len;
				while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
			}
			con.disconnect();
			
			System.out.println("[Telegram] Save file Done: " + outFile.getAbsolutePath());
			sendTelegram( "✅ Save file Done\n📁 " + outFile.getName()
			+ "\n📂 " + outFile.getAbsolutePath());
			
			// ── Image File이면 PC 화면에 즉h 표시
			if (isImageFile(outFile) && handler != null) {
				final java.io.File imgFile = outFile;
				javax.swing.SwingUtilities.invokeLater(() -> handler.showImage(imgFile));
			}
			// ── Media File이면 wmplayer로 즉h 재생
			if (isMediaFile(outFile) && handler != null) {
				final java.io.File mediaFile = outFile;
				new Thread(() -> handler.playMedia(mediaFile), "TelegramMediaPlay").start();
			}
			
			} catch (Exception e) {
			System.out.println("[Telegram] File receive error: " + e.getMessage());
			sendTelegram( "❌ File receive failed: " + e.getMessage());
		}
	}
	
	/** Image Files 여부 판별 (확장자 기준) */
	private static boolean isImageFile(java.io.File f) {
		String n = f.getName().toLowerCase();
		return n.endsWith(".jpg") || n.endsWith(".jpeg")
		|| n.endsWith(".png") || n.endsWith(".gif")
		|| n.endsWith(".bmp") || n.endsWith(".webp");
	}
	
	/** Media File 여부 판별 (확장자 기준) */
	private static boolean isMediaFile(java.io.File f) {
		String n = f.getName().toLowerCase();
		return n.endsWith(".mp3") || n.endsWith(".mp4")
		|| n.endsWith(".wav") || n.endsWith(".m4a")
		|| n.endsWith(".aac") || n.endsWith(".ogg")
		|| n.endsWith(".wma") || n.endsWith(".avi");
	}
	
	/**
		* Windows File명 금지 문자 제거/치환.
		* 금지 문자: \ / : * ? " < > | #
		* 제어문자(0x00~0x1F) 도 제거.
		* 결과가 비거나 점(.)만 남으면 "file" 로 대체.
	*/
	private static String sanitizeFileName(String name) {
		if (name == null || name.isEmpty()) return "file";
		// Windows 금지 문자 → _
		String s = name.replaceAll("[\\\\/:*?\"<>|#]", "_");
		// 제어문자 제거
		s = s.replaceAll("[\\x00-\\x1F]", "");
		// 앞뒤 공백·점 제거
		s = s.trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
		return s.isEmpty() ? "file" : s;
	}
	
	/** getFile API로 file_path, file_size 동h 조회. [0]=path, [1]=size(-1이면 None) */
	private String[] getFileInfo(String fileId) {
		try {
			String apiUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
			HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
			con.setConnectTimeout(8000);
			con.setReadTimeout(8000);
			int httpCode = con.getResponseCode();
			// 400 = File이 너무 커서 텔레그램이 제공 불가 (실제 50MB 초과)
			if (httpCode != 200) {
				System.out.println("[Telegram] getFileInfo HTTP " + httpCode + " -> file unavailable (estimated over 50MB)");
				con.disconnect();
				return new String[]{null, "-1"};  // path=null → 호출부에서 Sizes과 에러 처리
			}
			java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			con.disconnect();
			String resp = sb.toString();
			Matcher pm = Pattern.compile("\"file_path\":\"([^\"]+)\"").matcher(resp);
			Matcher sm = Pattern.compile("\"file_size\":(\\d+)").matcher(resp);
			String path = pm.find() ? pm.group(1) : null;
			long   size = sm.find() ? Long.parseLong(sm.group(1)) : -1L;
			System.out.println("[Telegram] getFileInfo path=" + path + " size=" + size);
			return new String[]{path, String.valueOf(size)};
			} catch (Exception e) {
			System.out.println("[Telegram] getFileInfo error: " + e.getMessage());
			return null;
		}
	}
	/** jar / exe / class 어떤 방식으로 실행해도 Executable의 Folder를 반환 */
	private static java.io.File resolveRunDir() {
		// 데이터 File은 항상 %APPDATA%\KootPanKing\ 고정
		// 실행File(exe/jar) 위치와 무관Other게 APPDATA 에만 Save
		String appData = System.getenv("APPDATA");
		if (appData == null) appData = System.getProperty("user.home");
		java.io.File dir = new java.io.File(appData
		+ java.io.File.separator + "KootPanKing");
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}
	// ── 위험 명령어 차단 목록 ─────────────────────────────────────
	/**
		* 실행을 금지할 명령어 키워드 목록.
		*
		* 비교 방식: 입력된 명령어를 소문자로 변환한 뒤
		* 아래 키워드 중 Other나라도 포함(contains)되면 차단.
		*
		* ─ File/디스크 File괴
		*   format, del /f, erase, rd /s, rmdir /s,
		*   diskpart, cipher /w, sdelete
		* ─ 시스템/계정 변경
		*   net user, net localgroup, reg delete, reg add,
		*   bcdedit, bootrec, attrib +h +s
		* ─ 보안 우회
		*   netsh firewall, netsh advfirewall,
		*   sc delete, sc stop, taskkill /f,
		*   icacls, cacls, takeown
		* ─ 악성 실행
		*   powershell -enc, powershell -exec bypass,
		*   wscript, cscript, mshta, rundll32,
		*   regsvr32 /u, certutil -decode
		* ─ 네트워크 공격
		*   ping -t, ping -n 9999, arp -d, route delete
		* ─ Other
		*   shutdown /s, shutdown /r, logoff,
		*   wmic process delete, vssadmin delete
	*/
	private static final java.util.List<String> BLOCKED_KEYWORDS =
	java.util.Arrays.asList(
		// File/디스크 File괴
		"format ",         // format c: 등 (뒤에 공백 required → format.com 단독 실행 허용)
		"del /f",
		"del/f",
		"erase /f",
		"rd /s",
		"rmdir /s",
		"diskpart",
		"cipher /w",
		"sdelete",
		// 시스템/계정 변경
		"net user",
		"net localgroup",
		"reg delete",
		"reg add",
		"bcdedit",
		"bootrec",
		// 보안 우회
		"netsh firewall",
		"netsh advfirewall",
		"sc delete",
		"sc stop",
		"taskkill /f",
		"icacls",
		"cacls",
		"takeown",
		// 악성 실행 패턴
		"-enc ",           // powershell -EncodedCommand
		"-exec bypass",    // powershell -ExecutionPolicy Bypass
		"wscript",
		"cscript",
		"mshta",
		"rundll32",
		"regsvr32 /u",
		"certutil -decode",
		// 네트워크 공격
		"ping -t",
		"arp -d",
		"route delete",
		// Other 위험
		"shutdown /s",
		"shutdown /r",
		"shutdown/s",
		"shutdown/r",
		"logoff",
		"wmic process delete",
		"vssadmin delete"
	);
	
	/**
		* 입력 명령어에 차단 키워드가 포함되어 있으면 해당 키워드를 반환.
		* 안전Other면 null 반환.
		*
		* @param command Enable자가 입력한 /cmd 뒤의 문자열
		* @return 차단 키워드 (없으면 null)
	*/
	private static String findBlockedKeyword(String command) {
		String lower = command.toLowerCase();
		for (String keyword : BLOCKED_KEYWORDS) {
			if (lower.contains(keyword)) return keyword.trim();
		}
		return null;
	}
	
	// ── 유틸 ──────────────────────────────────────────────────────
	/*
		@SuppressWarnings("deprecation")
		private static URL toUrl(String s) {
		try { return new URL(s); }
		catch (Exception e) { throw new RuntimeException(e); }
		}
	*/
	private static URL toUrl(String s) {
		try {
			return URI.create(s).toURL();
			} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
	// ── 텔레그램 Settings 다이얼로그 (JavaFX) ────────────────────────
	/**
		* 텔레그램 Settings 다이얼로그 (JavaFX Stage).
		* FX Application Thread 에서 호출해야 한다.
		*
		* @param ownerStage 부모 Stage
	*/
	public void showTelegramDialog(Stage ownerStage) {
		Stage dlg = new Stage();
		dlg.initOwner(ownerStage);
		dlg.initModality(Modality.NONE);
		dlg.initStyle(StageStyle.UTILITY);
		dlg.setTitle("✈️ Telegram Settings");
		dlg.setAlwaysOnTop(true);
		
		// ── Settings 영역 (GridPane) ─────────────────────────────
		GridPane cfg = new GridPane();
		cfg.setHgap(8);
		cfg.setVgap(6);
		cfg.setPadding(new Insets(8));
		
		TextField tokenField  = new TextField(botToken);
		TextField chatIdField = new TextField(myChatId);
		chatIdField.setPromptText("Send /start to @userinfobot → get Chat ID");
		tokenField.setPrefWidth(300);
		
		cfg.add(new Label("Bot Token:"), 0, 0);
		cfg.add(tokenField,              1, 0);
		cfg.add(new Label("Chat ID:"),   0, 1);
		cfg.add(chatIdField,             1, 1);
		
		CheckBox pollingCb = new CheckBox("🎮 Remote Control enabled (poll every 5s)");
		pollingCb.setSelected(polling);
		pollingCb.setTooltip(new Tooltip("Receive commands like /help /screenshot /shutdown from Telegram"));
		GridPane.setColumnSpan(pollingCb, 2);
		cfg.add(pollingCb, 0, 2);
		
		Label hintLabel = new Label("※ Remote: /help /capture /screenshot /shutdown /reboot");
		hintLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 11;");
		GridPane.setColumnSpan(hintLabel, 2);
		cfg.add(hintLabel, 0, 3);
		
		TitledPane cfgPane = new TitledPane("Settings", cfg);
		cfgPane.setCollapsible(false);
		
		// ── 메시지 영역 ──────────────────────────────────────
		TextArea msgArea = new TextArea(
			"Hello!\n\nThis is a test message from [KootPanKing] via Telegram.\n\n"
		+ new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
		msgArea.setWrapText(true);
		msgArea.setPrefRowCount(5);
		
		TitledPane msgPane = new TitledPane("Message", msgArea);
		msgPane.setCollapsible(false);
		
		// ── 첨부File 영역 ────────────────────────────────────
		java.util.List<File> attachedFiles = new java.util.ArrayList<>();
		ObservableList<String> fileItems   = FXCollections.observableArrayList();
		ListView<String> fileList = new ListView<>(fileItems);
		fileList.setPrefHeight(80);
		
		Button addFileBtn = new Button("📎 Add File");
		Button addImgBtn  = new Button("🖼 Add Image");
		Button captureBtn = new Button("📷 Capture Clock");
		Button removeBtn  = new Button("🗑 Remove");
		
		addFileBtn.setOnAction(e -> {
			FileChooser fc = new FileChooser();
			fc.setTitle("Select file to attach");
			java.util.List<File> files = fc.showOpenMultipleDialog(dlg);
			if (files != null) {
				for (File f : files) {
					attachedFiles.add(f);
					fileItems.add(f.getName() + "  (" + (f.length() / 1024) + " KB)");
				}
			}
		});
		
		addImgBtn.setOnAction(e -> {
			FileChooser fc = new FileChooser();
			fc.setTitle("Select image to attach");
			fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
			"Images", "*.jpg","*.jpeg","*.png","*.gif","*.bmp"));
			// "Image", "*.jpg","*.jpeg","*.png","*.gif","*.bmp","*.webp"));
			java.util.List<File> files = fc.showOpenMultipleDialog(dlg);
			if (files != null) {
				for (File f : files) {
					attachedFiles.add(f);
					fileItems.add("🖼 " + f.getName() + "  (" + (f.length() / 1024) + " KB)");
				}
			}
		});
		
		captureBtn.setOnAction(e -> {
			try {
				File capFile = (handler != null) ? handler.captureClockScreen() : null;
				if (capFile != null) {
					attachedFiles.add(capFile);
					fileItems.add("📷 " + capFile.getName() + "  (" + (capFile.length() / 1024) + " KB)");
					fxAlert(dlg, Alert.AlertType.INFORMATION, "Capture complete", "Clock screen captured.");
				}
				} catch (Exception ex) {
				fxAlert(dlg, Alert.AlertType.ERROR, "Capture failed", ex.getMessage());
			}
		});
		
		removeBtn.setOnAction(e -> {
			int idx = fileList.getSelectionModel().getSelectedIndex();
			if (idx >= 0) { attachedFiles.remove(idx); fileItems.remove(idx); }
		});
		
		HBox attachBtns = new HBox(4, addFileBtn, addImgBtn, captureBtn, removeBtn);
		VBox attachBox  = new VBox(4, fileList, attachBtns);
		TitledPane attachPane = new TitledPane("Attachments", attachBox);
		attachPane.setCollapsible(false);
		
		// ── Other단 버튼 영역 ───────────────────────────────────
		Label statusLabel = new Label(" ");
		statusLabel.setStyle("-fx-text-fill: green;");
		
		Button sendBtn  = new Button("✈️ Send");
		Button closeBtn = new Button("Close");
		sendBtn.setStyle("-fx-background-color:#0088cc; -fx-text-fill:white; -fx-font-weight:bold;");
		
		sendBtn.setOnAction(e -> {
			botToken = tokenField.getText().trim();
			myChatId = chatIdField.getText().trim();
			polling  = pollingCb.isSelected();
			String chatId = myChatId;
			String text   = msgArea.getText().trim();
			
			if (botToken.isEmpty()) {
				fxAlert(dlg, Alert.AlertType.WARNING, "Telegram", "Please enter the Bot Token."); return;
			}
			if (chatId.isEmpty()) {
				fxAlert(dlg, Alert.AlertType.WARNING, "Telegram", "Please enter the Chat ID."); return;
			}
			
			sendBtn.setDisable(true);
			statusLabel.setText("Sending...");
			statusLabel.setStyle("-fx-text-fill: orange;");
			
			new Thread(() -> {
				StringBuilder result = new StringBuilder();
				boolean anyError = false;
				
				if (!text.isEmpty()) {
					try   { sendTelegram( text); result.append("✅ Text sent\n"); }
					catch (Exception ex) {
						result.append("❌ Text send failed: ").append(ex.getMessage()).append("\n");
						anyError = true;
					}
				}
				for (File f : attachedFiles) {
					try   { sendFile(chatId, f); result.append("✅ ").append(f.getName()).append(" sent\n"); }
					catch (Exception ex) {
						result.append("❌ ").append(f.getName()).append(" failed: ").append(ex.getMessage()).append("\n");
						anyError = true;
					}
				}
				
				final String  finalResult = result.toString();
				final boolean hasError    = anyError;
				Platform.runLater(() -> {
					sendBtn.setDisable(false);
					statusLabel.setStyle(hasError ? "-fx-text-fill:red;" : "-fx-text-fill:green;");
					statusLabel.setText(hasError ? "Some failed" : "Sent ✅");
					fxAlert(dlg,
						hasError ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION,
						"Send result",
					finalResult.isEmpty() ? "Nothing to send." : finalResult);
				});
			}, "TelegramSend").start();
		});
		
		closeBtn.setOnAction(e -> {
			botToken = tokenField.getText().trim();
			myChatId = chatIdField.getText().trim();
			polling  = pollingCb.isSelected();
			if (polling) startPolling();
			else         stopPolling();
			if (handler != null) handler.saveConfig();
			dlg.close();
		});
		
		HBox btnRow = new HBox(8, statusLabel, sendBtn, closeBtn);
		btnRow.setAlignment(Pos.CENTER_RIGHT);
		btnRow.setPadding(new Insets(4, 0, 0, 0));
		
		// ── All 레이아웃 조립 ───────────────────────────────
		VBox root = new VBox(8, cfgPane, msgPane, attachPane, btnRow);
		root.setPadding(new Insets(12));
		
		dlg.setScene(new Scene(root, 520, 500));
		dlg.show();
	}
	
	/** JavaFX Alert 헬퍼 (FX 스레드에서 호출) */
	private static void fxAlert(Stage owner, Alert.AlertType type, String title, String msg) {
		Alert a = new Alert(type);
		a.initOwner(owner);
		a.setTitle(title);
		a.setHeaderText(null);
		a.setContentText(msg);
		a.show();
	}
	
	// ── 텔레그램 Info HTML File 열기 ─────────────────────────────
	public void showTelegramHelp(Stage ownerStage) {
		try {
			// TELEGRAM_help.txt Path: APP_DIR 기준
			java.io.File txtFile = new java.io.File(appDir.isEmpty() ? "." : appDir, "TELEGRAM_help.txt");
			
			String content;
			if (txtFile.exists()) {
				java.io.BufferedReader br = new java.io.BufferedReader(
					new java.io.InputStreamReader(
					new java.io.FileInputStream(txtFile), "UTF-8"));
					StringBuilder sb = new StringBuilder();
					String line;
					while ((line = br.readLine()) != null) sb.append(line).append("\n");
					br.close();
					content = sb.toString();
					} else {
					content = "TELEGRAM_help.txt not found.\nPlease place TELEGRAM_help.txt in the same folder as the executable.";
			}
			
			// URL → <a href> Links 변환 후 HTML 생성
			String escaped = content
			.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
			String html = escaped.replaceAll(
				"(https?://[\\S]+)",
			"<a href='$1' target='_blank'>$1</a>");
			
			java.io.File htmlFile = java.io.File.createTempFile("telegram_help_", ".html");
			htmlFile.deleteOnExit();
			try (java.io.PrintWriter pw = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
				pw.println("<!DOCTYPE html><html><head>");
				pw.println("<meta charset='UTF-8'>");
				pw.println("<title>Telegram Setup Guide</title>");
				pw.println("<style>");
				pw.println("body { font-family: 'Malgun Gothic', monospace;");
				pw.println("       background:#1a1a2e; color:#e0e0e0;");
				pw.println("       padding:30px; line-height:1.7; }");
				pw.println("pre  { white-space:pre-wrap; font-size:14px; }");
				pw.println("a    { color:#4fc3f7; font-weight:bold; }");
				pw.println("a:hover { color:#81d4fa; }");
				pw.println("</style></head><body><pre>");
				pw.println(html);
				pw.println("</pre></body></html>");
			}
			java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
			
			} catch (Exception e) {
			Platform.runLater(() ->
			fxAlert(ownerStage, Alert.AlertType.ERROR, "Error", "Failed to open guide: " + e.getMessage()));
		}
	}
}		