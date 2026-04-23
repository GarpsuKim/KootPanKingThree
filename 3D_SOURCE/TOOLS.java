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

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.Parent;
import javafx.scene.layout.Region;

import javax.swing.SwingUtilities;
import javafx.application.Platform;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

// -------- AliveStatusAgent
import java.io.InputStream;
import java.io.OutputStream;
// import java.net.HttpURLConnection;
// import java.net.URI;
// import java.nio.charset.StandardCharsets;
// import java.text.SimpleDateFormat;
// import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
// -------- AliveStatusAgent

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
	
	/**
		* AliveStatusAgent
		*
		* 역할
		*  1) 상태판 메시지 1개 유지
		*  2) 15초마다 상태판 버튼 텍스트를 마지막 시각(HH:mm:ss)로 갱신
		*
		* 주의
		*  - 메인 앱 내부 스레드이므로 메인 앱 종료 시 함께 종료됨
		*  - 상태판 메시지 생성 / 버튼 갱신은 Telegram Bot HTTP API 직접 호출
		*  - 일반 alive 텍스트 메시지는 보내지 않음
	*/
	public static class AliveStatusAgent {
		private static final long TEST_INTERVAL_MS = 15_000L;
		private static final long WARN_SEC = 60L;
		private static final long DEAD_SEC = 180L;

		private final AtomicBoolean running = new AtomicBoolean(false);
		private volatile long lastAliveTs = 0L;
		private Thread worker;

		/** 그룹 상태판 메시지 ID */
		private volatile long statusMessageId = -1L;
		/** 1:1 alive 메시지 ID */
		private volatile long privateAliveMessageId = -1L;
		/** 그룹 alive 메시지 ID */
		private volatile long groupAliveMessageId = -1L;

		/** 중복 초기 생성 방지 */
		private final Object statusLock = new Object();

		/** 상대 서버 감시: groupMessageId → 마지막으로 edited_message 수신한 시각(ms) */
		private final java.util.concurrent.ConcurrentHashMap<Long, Long> peerLastSeenMs
			= new java.util.concurrent.ConcurrentHashMap<>();
		/** 상대 서버 감시: groupMessageId → 서버 이름 */
		private final java.util.concurrent.ConcurrentHashMap<Long, String> peerNames
			= new java.util.concurrent.ConcurrentHashMap<>();

		public void start() {
			if (!running.compareAndSet(false, true)) {
				System.out.println("[AliveStatusAgent] already running");
				return;
			}
			lastAliveTs = System.currentTimeMillis();

			worker = new Thread(() -> {
				System.out.println("[AliveStatusAgent] worker started");
				try {
					loadMessageIds();
					ensureStatusMessage();

					// 첫 sync → groupAliveMessageId 확정 → HELLO broadcast
					syncPinnedAliveMessages();
					sendHello();

					while (running.get()) {
						try {
							ensureStatusMessage();   // statusMessageId=-1 이면 재생성
							markAlive();
							syncPinnedAliveMessages();
							updateStatusButton();
							checkPeers();
							Thread.sleep(TEST_INTERVAL_MS);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							break;
						} catch (Exception loopEx) {
							System.out.println("[AliveStatusAgent] loop error: " + loopEx.getMessage());
							AppLogger.logException(loopEx);
							safeSleep(5000L);
						}
					}
				} catch (Exception e) {
					System.out.println("[AliveStatusAgent] worker Exception: " + e.getMessage());
					AppLogger.logException(e);
				} finally {
					running.set(false);
					System.out.println("[AliveStatusAgent] worker stopped");
				}
			}, "AliveStatusAgent");

			worker.setDaemon(true);
			worker.start();
		}

		public void stop() {
			running.set(false);
			if (worker != null) {
				worker.interrupt();
			}
		}

		public boolean isRunning() {
			return running.get();
		}

		private String getStatusChatId() {
			String chatId = AppContext.get("tg.alive.statusChatId", "").trim();
			if (chatId.isEmpty()) {
				chatId = AppContext.get("tg.myChatId", "").trim();
			}
			return chatId;
		}

		private String getPrivateChatId() {
			return AppContext.get("tg.myChatId", "").trim();
		}

		private String getServerName() {
			String name = AppContext.get("tg.alive.serverName", "").trim();
			if (!name.isEmpty()) return name;
			try {
				return InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				return "UNKNOWN";
			}
		}

		private void loadMessageIds() {
			statusMessageId = parseLongConfig("tg.alive.status.messageId");
			privateAliveMessageId = parseLongConfig("tg.alive.private.messageId");
			groupAliveMessageId = parseLongConfig("tg.alive.group.messageId");

			System.out.println("[AliveStatusAgent] loaded statusMessageId=" + statusMessageId);
			System.out.println("[AliveStatusAgent] loaded privateAliveMessageId=" + privateAliveMessageId);
			System.out.println("[AliveStatusAgent] loaded groupAliveMessageId=" + groupAliveMessageId);
			loadPeers();
		}

		private long parseLongConfig(String key) {
			try {
				String v = AppContext.get(key, "").trim();
				if (!v.isEmpty()) return Long.parseLong(v);
			} catch (Exception ignored) {
			}
			return -1L;
		}

		private void saveStatusMessageId(long id) {
			saveMessageId("tg.alive.status.messageId", id);
		}

		private void savePrivateAliveMessageId(long id) {
			saveMessageId("tg.alive.private.messageId", id);
		}

		private void saveGroupAliveMessageId(long id) {
			saveMessageId("tg.alive.group.messageId", id);
		}

		private void saveMessageId(String key, long id) {
			try {
				AppContext.set(key, String.valueOf(id));
				AppContext.save();
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] saveMessageId failed: " + key + " / " + e.getMessage());
			}
		}

		/**
		 * 상태판 메시지 1개를 최초 1회 생성
		 */
		private void ensureStatusMessage() throws Exception {
			if (statusMessageId > 0) return;

			synchronized (statusLock) {
				if (statusMessageId > 0) return;

				String botToken = AppContext.get("tg.botToken", "").trim();
				String chatId = getStatusChatId();

				if (botToken.isEmpty() || chatId.isEmpty()) {
					throw new IllegalStateException("tg.botToken / statusChatId is empty");
				}

				String serverName = getServerName();

				String jsonBody =
					"{"
					+ "\"chat_id\":\"" + esc(chatId) + "\","
					+ "\"text\":\"🖥 " + esc(serverName) + "\","
					+ "\"reply_markup\":{"
					+ "\"inline_keyboard\":[["
					+ "{"
					+ "\"text\":\"" + esc(buildStatusText()) + "\","
					+ "\"callback_data\":\"alive_status\""
					+ "}"
					+ "]]"
					+ "}"
					+ "}";

				String response = postJson(botToken, "sendMessage", jsonBody);
				long parsedId = parseMessageId(response);

				if (parsedId <= 0) {
					throw new IllegalStateException("status message_id parse failed: " + response);
				}

				statusMessageId = parsedId;
				saveStatusMessageId(parsedId);
				System.out.println("[AliveStatusAgent] status message created: " + statusMessageId);
			}
		}

		private void syncPinnedAliveMessages() throws Exception {
			String privateChatId = getPrivateChatId();
			String groupChatId = getStatusChatId();
			String aliveText = buildAliveMessageText();

			privateAliveMessageId = upsertPinnedMessage(
				privateChatId,
				privateAliveMessageId,
				aliveText,
				"private"
			);
			if (privateAliveMessageId > 0) {
				savePrivateAliveMessageId(privateAliveMessageId);
			}

			if (!groupChatId.isEmpty() && !groupChatId.equals(privateChatId)) {
				groupAliveMessageId = upsertPinnedMessage(
					groupChatId,
					groupAliveMessageId,
					aliveText,
					"group"
				);
				if (groupAliveMessageId > 0) {
					saveGroupAliveMessageId(groupAliveMessageId);
				}
			} else {
				System.out.println("[AliveStatusAgent] group pinned alive skipped: statusChatId == myChatId or empty");
			}
		}

		private long upsertPinnedMessage(String chatId, long currentMessageId, String text, String label) throws Exception {
			if (chatId == null || chatId.trim().isEmpty()) {
				System.out.println("[AliveStatusAgent] " + label + " pinned message skipped: empty chatId");
				return -1L;
			}

			if (currentMessageId > 0) {
				try {
					editMessageText(chatId, currentMessageId, text);
					pinMessage(chatId, currentMessageId);
					System.out.println("[AliveStatusAgent] " + label + " pinned message edited");
					return currentMessageId;
				} catch (Exception editEx) {
					System.out.println("[AliveStatusAgent] " + label + " edit failed, recreate: " + editEx.getMessage());
				}
			}

			long newMessageId = sendMessageText(chatId, text, label);
			if (newMessageId > 0) {
				pinMessage(chatId, newMessageId);
			}
			return newMessageId;
		}

		private long sendMessageText(String chatId, String text, String label) throws Exception {
			String botToken = AppContext.get("tg.botToken", "").trim();
			if (botToken.isEmpty()) {
				System.out.println("[AliveStatusAgent] " + label + " send skipped: empty token");
				return -1L;
			}

			String jsonBody =
				"{"
				+ "\"chat_id\":\"" + esc(chatId) + "\","
				+ "\"text\":\"" + esc(text) + "\""
				+ "}";

			String response = postJson(botToken, "sendMessage", jsonBody);
			long messageId = parseMessageId(response);
			System.out.println("[AliveStatusAgent] " + label + " message sent: " + messageId + " / " + shorten(response));
			return messageId;
		}

		private void editMessageText(String chatId, long messageId, String text) throws Exception {
			String botToken = AppContext.get("tg.botToken", "").trim();
			if (botToken.isEmpty()) {
				throw new IllegalStateException("bot token is empty");
			}

			String jsonBody =
				"{"
				+ "\"chat_id\":\"" + esc(chatId) + "\","
				+ "\"message_id\":" + messageId + ","
				+ "\"text\":\"" + esc(text) + "\""
				+ "}";

			postJson(botToken, "editMessageText", jsonBody);
		}

		private void pinMessage(String chatId, long messageId) {
			try {
				String botToken = AppContext.get("tg.botToken", "").trim();
				if (botToken.isEmpty() || chatId == null || chatId.trim().isEmpty() || messageId <= 0) {
					return;
				}

				String jsonBody =
					"{"
					+ "\"chat_id\":\"" + esc(chatId) + "\","
					+ "\"message_id\":" + messageId + ","
					+ "\"disable_notification\":true"
					+ "}";

				String response = postJson(botToken, "pinChatMessage", jsonBody);
				System.out.println("[AliveStatusAgent] pinned: " + chatId + " / " + messageId + " / " + shorten(response));
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] pin failed: " + e.getMessage());
			}
		}

		private void markAlive() {
			lastAliveTs = System.currentTimeMillis();
		}

		private String getStatusIcon(long diffSec) {
			if (diffSec < WARN_SEC) return "🟢";
			if (diffSec < DEAD_SEC) return "🟡";
			return "🔴";
		}

		private String hhmmssFromMillis(long ts) {
			if (ts <= 0L) return "--:--:--";
			return new SimpleDateFormat("HH:mm:ss").format(new Date(ts));
		}

		private String buildStatusText() {
			long ts = lastAliveTs;
			long now = System.currentTimeMillis();
			long diffSec = ts <= 0L ? Long.MAX_VALUE : Math.max(0L, (now - ts) / 1000L);
			String icon = getStatusIcon(diffSec);
			String time = hhmmssFromMillis(ts);
			return icon + " LAST ALIVE " + time;
		}

		private String buildAliveMessageText() {
			long ts = lastAliveTs;
			long now = System.currentTimeMillis();
			long diffSec = ts <= 0L ? Long.MAX_VALUE : Math.max(0L, (now - ts) / 1000L);
			String lamp = getStatusIcon(diffSec);
			String serverName = getServerName();
			String time = hhmmssFromMillis(ts);
			return lamp + " [I am alive] " + serverName + " " + time;
		}

		/**
		 * 상태판 버튼만 갱신
		 */
		private void updateStatusButton() throws Exception {
			if (statusMessageId <= 0) return;

			String botToken = AppContext.get("tg.botToken", "").trim();
			String chatId = getStatusChatId();

			if (botToken.isEmpty() || chatId.isEmpty()) {
				System.out.println("[AliveStatusAgent] button update skipped: token/chatId empty");
				return;
			}

			String jsonBody =
				"{"
				+ "\"chat_id\":\"" + esc(chatId) + "\","
				+ "\"message_id\":" + statusMessageId + ","
				+ "\"reply_markup\":{"
				+ "\"inline_keyboard\":[["
				+ "{"
				+ "\"text\":\"" + esc(buildStatusText()) + "\","
				+ "\"callback_data\":\"alive_status\""
				+ "}"
				+ "]]"
				+ "}"
				+ "}";

			try {
				String response = postJson(botToken, "editMessageReplyMarkup", jsonBody);
				System.out.println("[AliveStatusAgent] status button updated: " + buildStatusText() + " / " + shorten(response));
			} catch (Exception e) {
				if (e.getMessage() != null && e.getMessage().contains("400")) {
					System.out.println("[AliveStatusAgent] status button not found (400), reset → will recreate");
					statusMessageId = -1L;
					saveStatusMessageId(-1L);
				} else {
					throw e;
				}
			}
		}

		public void updatePinnedMessagesNow(String text) {
			try {
				String privateChatId = getPrivateChatId();
				if (privateAliveMessageId > 0 && privateChatId != null && !privateChatId.trim().isEmpty()) {
					editMessageText(privateChatId, privateAliveMessageId, text);
					pinMessage(privateChatId, privateAliveMessageId);
				}
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] private pin update failed: " + e.getMessage());
			}
			try {
				String groupChatId = getStatusChatId();
				String privateChatId = getPrivateChatId();
				if (groupAliveMessageId > 0 && groupChatId != null && !groupChatId.trim().isEmpty() && !groupChatId.equals(privateChatId)) {
					editMessageText(groupChatId, groupAliveMessageId, text);
					pinMessage(groupChatId, groupAliveMessageId);
				}
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] group pin update failed: " + e.getMessage());
			}
		}
		
		private String postJson(String botToken, String method, String jsonBody) throws Exception {
			String apiUrl = "https://api.telegram.org/bot" + botToken + "/" + method;

			HttpURLConnection con = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setConnectTimeout(10_000);
			con.setReadTimeout(10_000);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

			byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = con.getOutputStream()) {
				os.write(bodyBytes);
			}

			int code = con.getResponseCode();
			InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();

			byte[] buf = new byte[4096];
			int n;
			StringBuilder sb = new StringBuilder();
			if (is != null) {
				while ((n = is.read(buf)) != -1) {
					sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
				}
				is.close();
			}
			con.disconnect();

			String response = sb.toString();
			if (code < 200 || code >= 300) {
				throw new IllegalStateException("Telegram API HTTP " + code + " / " + response);
			}
			return response;
		}

		private long parseMessageId(String json) {
			try {
				int idx = json.indexOf("\"message_id\":");
				if (idx < 0) return -1L;

				int start = idx + "\"message_id\":".length();
				while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
					start++;
				}

				int end = start;
				while (end < json.length()) {
					char c = json.charAt(end);
					if ((c >= '0' && c <= '9') || c == '-') {
						end++;
					} else {
						break;
					}
				}

				return Long.parseLong(json.substring(start, end).trim());
			} catch (Exception e) {
				return -1L;
			}
		}

		// ── Peer 서버 교차 감시 ─────────────────────────────────────────

		/**
		 * ini의 tg.alive.peers 에서 기존에 발견된 peer 목록 로드.
		 * 형식: tg.alive.peers = PC-A:12345,PC-B:67890
		 */
		private void loadPeers() {
			String peersStr = AppContext.get("tg.alive.peers", "").trim();
			if (peersStr.isEmpty()) {
				System.out.println("[AliveStatusAgent] no peers in ini");
				return;
			}
			long now = System.currentTimeMillis();
			for (String entry : peersStr.split(",")) {
				String[] parts = entry.trim().split(":");
				if (parts.length != 2) continue;
				try {
					String name  = parts[0].trim();
					long   msgId = Long.parseLong(parts[1].trim());
					peerNames.put(msgId, name);
					peerLastSeenMs.put(msgId, now);
					System.out.println("[AliveStatusAgent] peer loaded: " + name + " / msgId=" + msgId);
				} catch (Exception ignored) {}
			}
		}

		/** 발견된 peer 목록을 ini에 저장 */
		private void savePeers() {
			StringBuilder sb = new StringBuilder();
			for (java.util.Map.Entry<Long, String> e : peerNames.entrySet()) {
				if (sb.length() > 0) sb.append(",");
				sb.append(e.getValue()).append(":").append(e.getKey());
			}
			try {
				AppContext.set("tg.alive.peers", sb.toString());
				AppContext.save();
				System.out.println("[AliveStatusAgent] peers saved: " + sb.toString());
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] peers save failed: " + e.getMessage());
			}
		}

		/**
		 * 시작 시 그룹에 자신의 존재를 알림.
		 * 형식: [ALIVE_HELLO] serverName:groupAliveMessageId
		 */
		public void sendHello() {
			String botToken    = AppContext.get("tg.botToken", "").trim();
			String groupChatId = getStatusChatId();
			if (botToken.isEmpty() || groupChatId.isEmpty() || groupAliveMessageId <= 0) {
				System.out.println("[AliveStatusAgent] sendHello skipped: token/chatId/msgId not ready");
				return;
			}
			String text = "[ALIVE_HELLO] " + esc(getServerName()) + ":" + groupAliveMessageId;
			try {
				sendMessageText(groupChatId, text, "hello");
				System.out.println("[AliveStatusAgent] hello sent: " + text);
			} catch (Exception e) {
				System.out.println("[AliveStatusAgent] hello send failed: " + e.getMessage());
			}
		}

		/**
		 * 폴링이 [ALIVE_HELLO] 메시지를 수신했을 때 호출.
		 * 신규 peer → 등록 + ini 저장 + sendHello() 로 ACK
		 * 기존 peer → lastSeen 갱신만
		 */
		public void onHelloReceived(String serverName, long msgId) {
			boolean isNew = !peerLastSeenMs.containsKey(msgId);
			peerNames.put(msgId, serverName);
			peerLastSeenMs.put(msgId, System.currentTimeMillis());
			if (isNew) {
				System.out.println("[AliveStatusAgent] new peer discovered: " + serverName + ":" + msgId);
				savePeers();
				sendHello();   // 상대방도 나를 등록할 수 있도록 ACK
			} else {
				System.out.println("[AliveStatusAgent] peer hello refresh: " + serverName + ":" + msgId);
			}
		}

		/**
		 * 폴링이 edited_message 를 수신했을 때 호출.
		 * peer 목록에 있는 messageId 면 lastSeen 갱신.
		 */
		public void onPeerMessageEdited(long messageId) {
			if (peerLastSeenMs.containsKey(messageId)) {
				peerLastSeenMs.put(messageId, System.currentTimeMillis());
				System.out.println("[AliveStatusAgent] peer heartbeat: "
					+ peerNames.getOrDefault(messageId, "?") + " / msgId=" + messageId);
			}
		}

		/**
		 * 매 루프마다 peer 상태 점검.
		 * WARN_SEC 이상 heartbeat 없으면 🟡, DEAD_SEC 이상이면 🔴 로 peer 메시지 업데이트.
		 */
		private void checkPeers() {
			if (peerLastSeenMs.isEmpty()) return;
			String botToken    = AppContext.get("tg.botToken", "").trim();
			String groupChatId = getStatusChatId();
			if (botToken.isEmpty() || groupChatId.isEmpty()) return;

			long now = System.currentTimeMillis();
			for (java.util.Map.Entry<Long, Long> e : peerLastSeenMs.entrySet()) {
				long   msgId    = e.getKey();
				long   lastSeen = e.getValue();
				long   diffSec  = (now - lastSeen) / 1000L;
				String name     = peerNames.getOrDefault(msgId, "UNKNOWN");

				if (diffSec < WARN_SEC) continue;   // 정상 → 건드리지 않음

				String icon        = diffSec < DEAD_SEC ? "🟡" : "🔴";
				String lastSeenStr = hhmmssFromMillis(lastSeen);
				String text        = icon + " [" + name + "] last seen " + lastSeenStr;
				try {
					editMessageText(groupChatId, msgId, text);
					System.out.println("[AliveStatusAgent] peer marked " + icon
						+ ": " + name + " (+" + diffSec + "s)");
				} catch (Exception ex) {
					System.out.println("[AliveStatusAgent] peer mark failed: "
						+ name + " / " + ex.getMessage());
				}
			}
		}

		private void safeSleep(long ms) {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}

		private String esc(String s) {
			if (s == null) return "";
			return s
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "");
		}

		private String shorten(String s) {
			if (s == null) return "";
			return s.length() <= 120 ? s : s.substring(0, 120) + "...";
		}
	}
	
	/*
		public class TelegramWebAppBridge {
		private final TelegramBot bot;
		private final TelegramBot.CommandHandler handler;
		private TelegramWebAppServer server;
		public TelegramWebAppBridge(TelegramBot bot, TelegramBot.CommandHandler handler) {
		if (bot == null) throw new IllegalArgumentException("bot is null");
		if (handler == null) throw new IllegalArgumentException("handler is null");
		this.bot = bot;
		this.handler = handler;
		}
		public synchronized void startFromAppContext() {
		boolean enabled = getBoolean("tg.webapp.enabled", true);
		if (!enabled) {
		System.out.println("[TelegramWebAppBridge] disabled by tg.webapp.enabled=false");
		return;
		}
		String bindHost = AppContext.get("tg.webapp.bindHost", "127.0.0.1").trim();
		int port = parseInt(AppContext.get("tg.webapp.port", "8787"), 8787);
		String allowedChatId = AppContext.get("tg.myChatId", "").trim();
		try {
		if (server != null) {
		System.out.println("[TelegramWebAppBridge] already started");
		return;
		}
		server = new TelegramWebAppServer(
		port,
		bindHost,
		allowedChatId,
		new TelegramWebAppServer.WebAppHandler() {
		@Override
		public String getStatusJson() throws Exception {
		return buildStatusJson();
		}
		
		@Override
		public String requestFullCaptureJson() throws Exception {
		return handleFullCapture();
		}
		
		@Override
		public String getTodayScheduleJson() throws Exception {
		return buildTodayScheduleJson();
		}
		
		@Override
		public String saveMemoJson(String text) throws Exception {
		return saveMemo(text);
		}
		}
		);
		server.start();
		System.out.println("[TelegramWebAppBridge] started");
		} catch (Exception e) {
		System.out.println("[TelegramWebAppBridge] start failed: " + e.getMessage());
		AppLogger.logException(e);
		}
		}
		public synchronized void stop() {
		try {
		if (server != null) {
		server.stop();
		server = null;
		System.out.println("[TelegramWebAppBridge] stopped");
		}
		} catch (Exception e) {
		System.out.println("[TelegramWebAppBridge] stop failed: " + e.getMessage());
		AppLogger.logException(e);
		}
		}
		private String buildStatusJson() throws Exception {
		String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String pcName = safeHostName();
		String userId = System.getProperty("user.name", "");
		String osName = System.getProperty("os.name", "") + " " + System.getProperty("os.version", "");
		String javaVer = System.getProperty("java.version", "");
		String localIp = safeLocalIp();
		String webAppUrl = AppContext.get("tg.webAppUrl", "").trim();
		return "{"
		+ "\"ok\":true,"
		+ "\"time\":\"" + j(now) + "\"," 
		+ "\"pcName\":\"" + j(pcName) + "\"," 
		+ "\"user\":\"" + j(userId) + "\"," 
		+ "\"localIp\":\"" + j(localIp) + "\"," 
		+ "\"os\":\"" + j(osName.trim()) + "\"," 
		+ "\"java\":\"" + j(javaVer) + "\"," 
		+ "\"webAppUrl\":\"" + j(webAppUrl) + "\""
		+ "}";
		}
		private String handleFullCapture() throws Exception {
		final File[] result = new File[1];
		SwingUtilities.invokeAndWait(() -> {
		try {
		result[0] = handler.captureFullScreen();
		} catch (Exception e) {
		throw new RuntimeException(e);
		}
		});
		if (result[0] == null || !result[0].exists()) {
		return errorJson("capture_failed", "capture result is empty");
		}
		String chatId = AppContext.get("tg.myChatId", "").trim();
		if (!chatId.isEmpty()) {
		try {
		bot.sendFile(chatId, result[0]);
		} catch (Exception sendEx) {
		return "{"
		+ "\"ok\":true,"
		+ "\"message\":\"capture created but telegram send failed\","
		+ "\"file\":\"" + j(result[0].getAbsolutePath()) + "\","
		+ "\"sendError\":\"" + j(sendEx.getMessage()) + "\""
		+ "}";
		}
		}
		return "{"
		+ "\"ok\":true,"
		+ "\"message\":\"capture sent\","
		+ "\"file\":\"" + j(result[0].getAbsolutePath()) + "\""
		+ "}";
		}
		private String buildTodayScheduleJson() throws Exception {
		if (bot.calendarService == null || !bot.calendarService.isInitialized()) {
		return errorJson("calendar_not_configured", "Google Calendar is not configured");
		}
		List<GoogleCalendarService.CalendarEvent> events = bot.calendarService.getToday();
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"ok\":true,");
		sb.append("\"title\":\"Today's schedule\",");
		sb.append("\"count\":").append(events == null ? 0 : events.size()).append(",");
		sb.append("\"events\":[");
		if (events != null) {
		boolean first = true;
		DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
		for (GoogleCalendarService.CalendarEvent e : events) {
		if (!first) sb.append(",");
		first = false;
		String start = e.startTime == null ? "" : e.startTime.format(tf);
		String end = e.endTime == null ? "" : e.endTime.format(tf);
		String title = e.summary == null ? "" : e.summary;
		String location = e.location == null ? "" : e.location;
		String description = e.description == null ? "" : e.description;
		sb.append("{")
		.append("\"start\":\"").append(j(start)).append("\",")
		.append("\"end\":\"").append(j(end)).append("\",")
		.append("\"title\":\"").append(j(title)).append("\",")
		.append("\"location\":\"").append(j(location)).append("\",")
		.append("\"description\":\"").append(j(description)).append("\"")
		.append("}");
		}
		}
		sb.append("]");
		sb.append("}");
		return sb.toString();
		}
		private String saveMemo(String text) throws Exception {
		String telegramPathS = AppContext.getAPP_DIR().replaceAll("[/\\\\]+$", "") + File.separator + "TELEGRAM";
		File telegramPathF = new File(telegramPathS);
		if (!telegramPathF.exists()) telegramPathF.mkdirs();
		
		String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File outFile = new File(telegramPathF, "TelegramWebApp_" + ts + ".txt");
		
		try (Writer w = new OutputStreamWriter(new java.io.FileOutputStream(outFile), StandardCharsets.UTF_8)) {
		w.write(text == null ? "" : text);
		}
		
		Platform.runLater(() -> {
		try {
		handler.openTextFile(outFile);
		} catch (Exception e) {
		System.out.println("[TelegramWebAppBridge] openTextFile failed: " + e.getMessage());
		}
		});
		
		return "{"
		+ "\"ok\":true,"
		+ "\"message\":\"memo saved\","
		+ "\"file\":\"" + j(outFile.getAbsolutePath()) + "\""
		+ "}";
		}
		
		private static boolean getBoolean(String key, boolean defaultValue) {
		String s = AppContext.get(key, String.valueOf(defaultValue)).trim();
		return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
		}
		
		private static int parseInt(String s, int defaultValue) {
		try {
		return Integer.parseInt(s == null ? "" : s.trim());
		} catch (Exception e) {
		return defaultValue;
		}
		}
		
		private static String safeHostName() {
		try {
		return InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
		return "";
		}
		}
		
		private static String safeLocalIp() {
		try {
		return InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
		return "";
		}
		}
		
		private static String errorJson(String code, String message) {
		return "{"
		+ "\"ok\":false,"
		+ "\"error\":\"" + j(code) + "\","
		+ "\"message\":\"" + j(message) + "\""
		+ "}";
		}
		
		private static String j(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
		.replace("\r", "");
		}
		}
	*/
	public static class DialogStyle {
		
		public static final String PAGE_BG =
        "linear-gradient(to bottom right, #fff8fc 0%, #ffeef7 45%, #ffe3f0 100%)";
		public static final String CARD_BG       = "rgba(255,255,255,0.82)";
		public static final String BORDER        = "rgba(209,79,146,0.28)";
		public static final String TITLE_COLOR   = "#6b2148";
		public static final String TEXT_COLOR    = "#6f4660";
		public static final String MUTED_COLOR   = "#8a6b7c";
		public static final String FIELD_BG      = "rgba(255,255,255,0.96)";
		public static final String PRIMARY       = "#d14f92";
		public static final String PRIMARY_HOVER = "#bf3f80";
		public static final String SOFT_BTN_BG   = "rgba(255,255,255,0.78)";
		public static final String SUCCESS       = "#1f7a4d";
		public static final String ERROR         = "#b33a3a";
		
		private DialogStyle() {}
		
		public static Stage createDialog(Stage owner, String title) {
			Stage dlg = new Stage();
			if (owner != null) dlg.initOwner(owner);
			dlg.initModality(Modality.APPLICATION_MODAL);
			dlg.initStyle(StageStyle.DECORATED);
			dlg.setAlwaysOnTop(true);
			dlg.setTitle(title);
			return dlg;
		}
		
		public static VBox createDialogRoot(
			String title,
			String subtitle,
			Node formBox,
			Node infoBox,
			Node resultLabel,
			Node buttonRow
			) {
			VBox header = new VBox(6,
				createDialogTitle(title),
				createDialogSubtitle(subtitle)
			);
			
			VBox root = new VBox(16, header, formBox, infoBox, resultLabel, buttonRow);
			root.setPadding(new Insets(22, 22, 28, 22));
			root.setStyle("-fx-background-color: " + PAGE_BG + ";");
			return root;
		}
		/*
			public static void applyDialogScene(Stage dlg, Region root, double minW, double minH) {
			Scene scene = new Scene(root);
			AppContext.applyGlobalFont(scene);
			dlg.setScene(scene);
			dlg.sizeToScene();
			
			double w = Math.max(dlg.getWidth(), minW);
			double h = Math.max(dlg.getHeight(), minH);
			
			dlg.setWidth(w);
			dlg.setHeight(h);
			
			root.setPrefWidth(w);
			root.setPrefHeight(h);
			
			dlg.setMinWidth(w);
			dlg.setMinHeight(h);
			}
		*/
		public static Label createDialogTitle(String text) {
			Label label = new Label(text);
			label.setStyle(
				"-fx-font-size: 22px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + TITLE_COLOR + ";"
			);
			return label;
		}
		
		public static Label createDialogSubtitle(String text) {
			Label label = new Label(text);
			label.setWrapText(true);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-text-fill: " + TEXT_COLOR + ";"
			);
			return label;
		}
		
		public static Label createFieldLabel(String text) {
			Label label = new Label(text);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + TITLE_COLOR + ";"
			);
			return label;
		}
		
		public static Label createResultLabel() {
			Label label = new Label("");
			label.setWrapText(true);
			label.setMinHeight(38);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + MUTED_COLOR + ";"
			);
			return label;
		}
		
		public static void setResultNeutral(Label label, String text) {
			label.setText(text);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + MUTED_COLOR + ";"
			);
		}
		
		public static void setResultSuccess(Label label, String text) {
			label.setText(text);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + SUCCESS + ";"
			);
		}
		
		public static void setResultError(Label label, String text) {
			label.setText(text);
			label.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + ERROR + ";"
			);
		}
		
		public static void styleInput(TextInputControl input) {
			input.setStyle(
				"-fx-background-color: " + FIELD_BG + ";" +
				"-fx-background-radius: 12;" +
				"-fx-border-color: " + BORDER + ";" +
				"-fx-border-radius: 12;" +
				"-fx-border-width: 1;" +
				"-fx-padding: 0 12 0 12;" +
				"-fx-font-size: 13px;" +
				"-fx-text-fill: #2d1d27;" +
				"-fx-prompt-text-fill: #a38494;"
			);
		}
		
		public static TextField createTextField(String text, String prompt) {
			TextField field = new TextField(text == null ? "" : text);
			field.setPrefHeight(40);
			field.setPromptText(prompt);
			styleInput(field);
			return field;
		}
		
		public static PasswordField createPasswordField(String text, String prompt) {
			PasswordField field = new PasswordField();
			field.setText(text == null ? "" : text);
			field.setPrefHeight(40);
			field.setPromptText(prompt);
			styleInput(field);
			return field;
		}
		
		public static CheckBox createInlineCheckBox(String text) {
			CheckBox checkBox = new CheckBox(text);
			checkBox.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-text-fill: " + TEXT_COLOR + ";"
			);
			return checkBox;
		}
		
		public static final class PasswordControls {
			public final PasswordField hiddenField;
			public final TextField visibleField;
			public final CheckBox showCheckBox;
			public final StackPane stack;
			
			public PasswordControls(
				PasswordField hiddenField,
				TextField visibleField,
				CheckBox showCheckBox,
				StackPane stack
				) {
				this.hiddenField = hiddenField;
				this.visibleField = visibleField;
				this.showCheckBox = showCheckBox;
				this.stack = stack;
			}
			
			public String getText() {
				return showCheckBox.isSelected()
                ? visibleField.getText().trim()
                : hiddenField.getText().trim();
			}
		}
		
		public static PasswordControls createPasswordControls(String value, String promptText) {
			PasswordField passField = createPasswordField(value, promptText);
			TextField passVisible = createTextField(value, promptText);
			passVisible.setVisible(false);
			passVisible.setManaged(false);
			
			CheckBox showPass = createInlineCheckBox("Show password");
			showPass.setOnAction(ev -> {
				boolean show = showPass.isSelected();
				if (show) passVisible.setText(passField.getText());
				else      passField.setText(passVisible.getText());
				
				passField.setVisible(!show);
				passField.setManaged(!show);
				passVisible.setVisible(show);
				passVisible.setManaged(show);
			});
			
			StackPane stack = new StackPane(passField, passVisible);
			return new PasswordControls(passField, passVisible, showPass, stack);
		}
		
		public static VBox createInfoBox(String title, String body) {
			Label infoTitle = new Label(title);
			infoTitle.setStyle(
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-text-fill: " + TITLE_COLOR + ";"
			);
			
			Label infoBody = new Label(body);
			infoBody.setWrapText(true);
			infoBody.setStyle(
				"-fx-font-size: 11px;" +
				"-fx-text-fill: " + MUTED_COLOR + ";"
			);
			
			VBox box = new VBox(4, infoTitle, infoBody);
			box.setPadding(new Insets(12));
			box.setStyle(
				"-fx-background-color: rgba(255,255,255,0.68);" +
				"-fx-background-radius: 14;" +
				"-fx-border-color: " + BORDER + ";" +
				"-fx-border-radius: 14;" +
				"-fx-border-width: 1;"
			);
			return box;
		}
		
		public static VBox createFormCard(Node... children) {
			VBox box = new VBox(14, children);
			box.setPadding(new Insets(18));
			box.setStyle(
				"-fx-background-color: " + CARD_BG + ";" +
				"-fx-background-radius: 18;" +
				"-fx-border-color: " + BORDER + ";" +
				"-fx-border-radius: 18;" +
				"-fx-border-width: 1;"
			);
			return box;
		}
		
		public static HBox createButtonRow(Node... buttons) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			
			HBox row = new HBox(10);
			row.setAlignment(Pos.CENTER_LEFT);
			
			if (buttons.length == 0) return row;
			
			row.getChildren().add(buttons[0]);
			if (buttons.length >= 2) row.getChildren().add(spacer);
			for (int i = 1; i < buttons.length; i++) {
				row.getChildren().add(buttons[i]);
			}
			return row;
		}
		
		public static Button createSoftButton(String text) {
			Button btn = new Button(text);
			btn.setPrefHeight(38);
			btn.setStyle(
				"-fx-background-color: " + SOFT_BTN_BG + ";" +
				"-fx-background-radius: 12;" +
				"-fx-border-color: " + BORDER + ";" +
				"-fx-border-radius: 12;" +
				"-fx-border-width: 1;" +
				"-fx-text-fill: " + TITLE_COLOR + ";" +
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-cursor: hand;"
			);
			return btn;
		}
		
		public static Button createSecondaryButton(String text) {
			Button btn = new Button(text);
			btn.setPrefHeight(38);
			btn.setStyle(
				"-fx-background-color: rgba(255,255,255,0.92);" +
				"-fx-background-radius: 12;" +
				"-fx-border-color: " + PRIMARY + ";" +
				"-fx-border-radius: 12;" +
				"-fx-border-width: 1.2;" +
				"-fx-text-fill: " + PRIMARY + ";" +
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-cursor: hand;"
			);
			return btn;
		}
		
		public static Button createPrimaryButton(String text) {
			Button btn = new Button(text);
			applyPrimaryButtonStyle(btn, false);
			btn.setPrefHeight(38);
			btn.setOnMouseEntered(e -> applyPrimaryButtonStyle(btn, true));
			btn.setOnMouseExited(e -> applyPrimaryButtonStyle(btn, false));
			return btn;
		}
		
		private static void applyPrimaryButtonStyle(Button btn, boolean hover) {
			btn.setStyle(
				"-fx-background-color: " + (hover ? PRIMARY_HOVER : PRIMARY) + ";" +
				"-fx-background-radius: 12;" +
				"-fx-border-radius: 12;" +
				"-fx-text-fill: white;" +
				"-fx-font-size: 12px;" +
				"-fx-font-weight: bold;" +
				"-fx-cursor: hand;"
			);
		}
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
					System.out.println("[NppInstall] already installed: " + getExePath());
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
			System.out.println("[NppInstall] extract archive: " + installDir);
			unzip(zipFile, installDir);
			
			try { Files.deleteIfExists(zipFile); Files.deleteIfExists(tempDir); }
			catch (Exception ignored) {}
			
			// 설치 후 Path 확인 및 ini Save
			if (Files.exists(getUserExePath())) {
				savePath(getUserExePath().toString());
				System.out.println("[NppInstall] Done: " + installDir);
				} else {
				System.out.println("[NppInstall] notepad++.exe not found after installation.");
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
					System.out.println("winget detected. trying installation with winget...");
					int code = installWithWinget();
					System.out.println("winget exit code: " + code);
					if (code == 0 && isAlreadyInstalled()) {
						System.out.println("Notepad++ Install Done (winget)");
						return;
					}
					System.out.println("winget install failed or verification failed. proceeding with direct download...");
					} else {
					System.out.println("winget not found. proceeding with direct download...");
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
			System.out.println("OS architecture: " + (is64 ? "64-bit" : "32-bit"));
			System.out.println("Installer URL: " + installerUrl);
			Path tempDir = Files.createTempDirectory("npp_install_");
			Path installer = tempDir.resolve(extractFileName(installerUrl));
			downloadFile(client, installerUrl, installer);
			System.out.println("Download Done: " + installer);
			int code = runInstaller(installer);
			System.out.println("install exit code: " + code);
			if (code == 0 && isAlreadyInstalled()) {
				System.out.println("Notepad++ Install Done (direct download)");
				} else if (code == 0) {
				System.out.println("installation command exited normally, but installation verification failed.");
				} else {
				System.out.println("installation failed");
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
					System.out.println("[ImageWindow] shown: " + imageFile.getName());
					
					} catch (Exception e) {
					System.out.println("[ImageWindow] show Failed: " + e.getMessage());
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
								System.out.println("[Camera] connection error (" + failCount + "/" + MAX_FAIL
								+ "), retrying in 3s: " + e.getMessage());
								if (failCount >= MAX_FAIL) {
									System.out.println("[Camera] consecutive " + MAX_FAIL + " failures -> auto stop");
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
					System.out.println("[Camera] Capture failed: no received frame");
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
					System.out.println("[Camera] save error: " + e.getMessage());
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
