import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Arrays;   // Arrays.asList()
/**
	* GmailSender - 순수 Java SMTP 메일 전송 클래스
	*
	* 외부 라이브러리 불필요.
	* Gmail SMTP + STARTTLS (포트 587) + AUTH LOGIN (Base64) 방식.
	*
	* 설정값(from, pass, lastTo)은 KootPanKing 이
	* clock_config.properties 에서 로드하여 필드에 직접 할당한다.
	GmailSender.getInstance().send(...);
*/
public class GmailSender {
	// 추가된 필드 (AppLogger 대신 외부 주입)
	public String exeFilePath = "";
	public String logFilePath = "";
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int    SMTP_PORT = 587;
    public static final String APP_SIGNATURE =
	"\n\n[3차원 끝판왕]에서 보내는 알림입니다.\n\n";
    // ── 설정 필드 (외부에서 직접 읽기/쓰기) ──────────────────────
    public String from    = "";  // Gmail 주소       (clock_config: emailFrom)
    public String pass    = "";  // Gmail 앱 비밀번호 (clock_config: emailPass)
    public String lastTo  = "";  // 마지막 수신자    (clock_config: emailLastTo)
    // ── 생성자 ────────────────────────────────────────────────────
    private volatile boolean initialized = false;
	private static final GmailSender INSTANCE = new GmailSender();
    private GmailSender() {}
	public static GmailSender getInstance() {
        return INSTANCE;
	}
	public synchronized  void init() {
		if (initialized) return;
		initialized = true;
		this.from   = AppContext.get("gmail.from", "");
		this.pass   = AppContext.get("gmail.pass", "");
		this.lastTo = AppContext.get("gmail.lastTo", "");
		this.exeFilePath = AppContext.theExePath;
		this.logFilePath = AppLogger.getLogFilePath();
	}
    // ── 공개 API ──────────────────────────────────────────────────
    /** 설정이 충분한지 여부 */
    public boolean isConfigured() {
        return !from.isEmpty() && !pass.isEmpty();
	}
    /**
		* 메일 전송 (수신자/제목/본문 직접 지정).
		* @throws Exception SMTP 오류 시
	*/
    public void sendOneSmtp(String to, String subject, String body) throws Exception {
        smtpSend(from, pass, from, to, subject, body);
        lastTo = to; // 마지막 수신자 갱신
	}
	public String testSend(String from, String pass, String to, List<File> attachs	)  {
		if (from == null || from.trim().isEmpty()) {
			System.out.println("[Gmail][testSend] 실패 [발신자 ID 미확인]");
			return "[Gmail][testSend] 실패 [발신자 ID 미확인]";
		}
		if (pass == null || pass.trim().isEmpty()) {
			System.out.println("[Gmail][testSend] 실패 [발신자 앱 비밀번호 미확인]");
			return "[Gmail][testSend] 실패 [발신자 앱 비밀번호 미확인]";
		}
		if (to == null || to.trim().isEmpty()) {
			System.out.println("[Gmail][testSend] 실패 [수신자 ID 미확인]");
			return "[Gmail][testSend] 실패 [수신자 ID 미확인]";
		}
		String now      = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
		String pcName = System.getenv("COMPUTERNAME");   // Windows();
		String userId   = System.getProperty("user.name");
		String osName   = System.getProperty("os.name") + " " + System.getProperty("os.version");
		String javaVer  = System.getProperty("java.version");
		String body = "[끝판왕3] 테스트 메일입니다..\n\n"
		+ "시작 시각: " + now + "\n\n"
		+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
		+ "PC 이름  : " + pcName  + "\n"
		+ "사용자   : " + userId  + "\n"
		+ "OS       : " + osName  + "\n"
		+ "Java     : " + javaVer + "\n"
		+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";
		String subject = "[끝판왕3] 테스트 메일입니다..\n\n";
		Map<String, String> env = System.getenv();
		String SyetemENV = "";
        for (Map.Entry<String, String> entry : env.entrySet()) {
            SyetemENV = SyetemENV + "\n" + entry.getKey() + " = " + entry.getValue();
		}
        Properties props = System.getProperties();
		String getPropertyValue = "";
        for (String key : props.stringPropertyNames()) {
            getPropertyValue = getPropertyValue + "\n" + key + " = " + props.getProperty(key);
		}
		body = body + "\n[SyetemENV]■■■■■\n" + SyetemENV + "\n[getPropertyValue]■■■■■\n" + getPropertyValue;
		// java.io.File attach = new java.io.File("");
		// List<File> attachs = Arrays.asList(  new File("C://Temp//a(1).jpg") , new File("C://Temp//a(2).jpg") );
		try {	sendOneSmtpWithAttachments(from, pass, to, subject, body , attachs );
//		try {	smtpSend(from, pass, from, to, subject, body);
			} catch (Exception e) {
            System.out.println("[Gmail][testSend] 실패: " + e.getMessage());
			return "[Gmail][testSend] 실패: " + e.getMessage();
		}
		return "";
	}
    /**
		* 알람 메일 전송 (AlarmEntry 정보 기반).
		* 오류는 콘솔 출력만 하고 예외를 던지지 않는다.
	*/
    public void sendAlarm(String toAddr, int hour, int minute, String msg) {
        String subj = "알람 " + String.format("%02d:%02d", hour, minute);
        System.out.println("[Gmail][sendAlarm] to=" + toAddr + " subj=" + subj);
        System.out.println("[Gmail][sendAlarm] body=\n" + msg);
        try {
            smtpSend(from, pass, from, toAddr, subj, msg);
            System.out.println("[Gmail][sendAlarm] 발송 완료 → " + toAddr);
			} catch (Exception e) {
            System.out.println("[Gmail][sendAlarm] 발송 실패: " + e.getMessage());
		}
	}
    /**
		* 시작 알림 메일 전송 (비동기).
		* from/pass/lastTo 가 모두 설정된 경우에만 전송.
	*/
    public void sendStartupNotice() {	    sendStartupNotice("") ;	}
    public void sendStartupNotice(String textContent) {
        if (!isConfigured() || lastTo.isEmpty()) {
            System.out.println("[Gmail][sendStartupNotice] 스킵 — from=" + from
			+ " lastTo=" + lastTo + " configured=" + isConfigured());
            return;
		}
        new Thread(() -> {
			try {
				Thread.sleep(10_000); // 10초 대기
				String now      = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				String pcName   = java.net.InetAddress.getLocalHost().getHostName();
				String userId   = System.getProperty("user.name");
				String osName   = System.getProperty("os.name") + " " + System.getProperty("os.version");
				String javaVer  = System.getProperty("java.version");
				String localIp  = java.net.InetAddress.getLocalHost().getHostAddress();
				String publicIp = getPublicIp();
				String body = APP_SIGNATURE
				+ "PC가 시작되었습니다.\n\n"
				+ "시작 시각: " + now + "\n\n"
				+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
				+ "PC 이름  : " + pcName  + "\n"
				+ "사용자   : " + userId  + "\n"
				+ "IP (내부) : " + localIp   + "\n"
				+ "IP (외부) : " + publicIp  + "\n"
				+ "OS       : " + osName  + "\n"
				+ "Java     : " + javaVer + "\n"
				+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
				+ "실행 파일: " + exeFilePath + "\n"
				+ "로그 파일: " + logFilePath + "\n"
				+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + "\n"
				+ textContent + "\n" ;
				System.out.println("[Gmail][sendStartupNotice] from=" + from + " to=" + lastTo);
				// System.out.println("[Gmail][sendStartupNotice] body=\n" + body);
				sendOneSmtp(lastTo, "PC 시작 알림", body);
				System.out.println("[Gmail][sendStartupNotice] 발송 완료 → " + lastTo);
				} catch (Exception e) {
				System.out.println("[Gmail][sendStartupNotice] 발송 실패: " + e.getMessage());
			}
		}, "StartupEmail").start();
	}
    /** 외부 공인 IP 조회 (api.ipify.org 사용) */
    private String getPublicIp() {
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
            return (ip != null && !ip.isEmpty()) ? ip.trim() : "(조회 실패)";
			} catch (Exception e) {
            return "(조회 실패)";
		}
	}
    /**
		* from/pass/lastTo 가 모두 설정된 경우에만 전송.
	*/
    public void sendShutdownNotice(Runnable afterSend) {
        if (!isConfigured() || lastTo.isEmpty()) {
            System.out.println("[Gmail][sendShutdownNotice] 스킵 — from=" + from
			+ " lastTo=" + lastTo + " configured=" + isConfigured());
            if (afterSend != null) afterSend.run();
            return;
		}
        new Thread(() -> {
            try {
                String body = APP_SIGNATURE + "PC가 종료됩니다.\n\n"
				+ new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                System.out.println("[Gmail][sendShutdownNotice] from=" + from + " to=" + lastTo);
                System.out.println("[Gmail][sendShutdownNotice] body=\n" + body);
                sendOneSmtp(lastTo, "PC 종료 알림", body);
                System.out.println("[Gmail][sendShutdownNotice] 발송 완료 → " + lastTo);
				} catch (Exception e) {
                System.out.println("[Gmail][sendShutdownNotice] 발송 실패: " + e.getMessage());
				} finally {
                if (afterSend != null) afterSend.run();
			}
		}, "ShutdownEmail").start();
	}
    /**
		* 종료 알림 메일 전송 - 제목/본문 직접 지정 버전 (텔레그램 원격 종료 등에 사용).
	*/
    public void sendShutdownNotice(Runnable afterSend, String subject, String body) {
        if (!isConfigured() || lastTo.isEmpty()) {
            System.out.println("[Gmail][sendShutdownNotice] 스킵 — from=" + from
			+ " lastTo=" + lastTo + " configured=" + isConfigured());
            if (afterSend != null) afterSend.run();
            return;
		}
        new Thread(() -> {
            try {
                System.out.println("[Gmail][sendShutdownNotice] from=" + from + " to=" + lastTo + " subj=" + subject);
                System.out.println("[Gmail][sendShutdownNotice] body=\n" + body);
                sendOneSmtp(lastTo, subject, body);
                System.out.println("[Gmail][sendShutdownNotice] 발송 완료 → " + lastTo);
				} catch (Exception e) {
                System.out.println("[Gmail][sendShutdownNotice] 발송 실패: " + e.getMessage());
				} finally {
                if (afterSend != null) afterSend.run();
			}
		}, "ShutdownEmail").start();
	}
    /**
		* 종료 알림 메일 동기 전송.
		* 호출 스레드에서 완료까지 블로킹. 콜백 없음.
	*/
    public void sendShutdownNoticeSync() {
        if (!isConfigured() || lastTo.isEmpty()) {
            System.out.println("[Gmail][sendShutdownNoticeSync] 스킵 — from=" + from
			+ " lastTo=" + lastTo + " configured=" + isConfigured());
            return;
		}
        try {
            String body = APP_SIGNATURE + "PC가 종료됩니다.\n\n"
			+ new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            System.out.println("[Gmail][sendShutdownNoticeSync] from=" + from + " to=" + lastTo);
            System.out.println("[Gmail][sendShutdownNoticeSync] body=\n" + body);
            sendOneSmtp(lastTo, "PC 종료 알림", body);
            System.out.println("[Gmail][sendShutdownNoticeSync] 발송 완료 → " + lastTo);
			} catch (Exception e) {
            System.out.println("[Gmail][sendShutdownNoticeSync] 발송 실패: " + e.getMessage());
		}
	}
    /**
		* 종료 알림 메일 동기 전송 - 제목/본문 직접 지정 버전.
		* 호출 스레드에서 완료까지 블로킹. 콜백 없음.
	*/
    public void sendShutdownNoticeSync(String subject, String body) {
        if (!isConfigured() || lastTo.isEmpty()) {
            System.out.println("[Gmail][sendShutdownNoticeSync] 스킵 — from=" + from
			+ " lastTo=" + lastTo + " configured=" + isConfigured());
            return;
		}
        try {
            System.out.println("[Gmail][sendShutdownNoticeSync] from=" + from + " to=" + lastTo + " subj=" + subject);
            System.out.println("[Gmail][sendShutdownNoticeSync] body=\n" + body);
            sendOneSmtp(lastTo, subject, body);
            System.out.println("[Gmail][sendShutdownNoticeSync] 발송 완료 → " + lastTo);
			} catch (Exception e) {
            System.out.println("[Gmail][sendShutdownNoticeSync] 발송 실패: " + e.getMessage());
		}
	}
    // ── 내부 SMTP 구현 ────────────────────────────────────────────
    /**
		* 순수 Java SMTP 전송.
		* Gmail SMTP + STARTTLS (포트 587) + AUTH LOGIN (Base64)
	*/
	void smtpSend(String user, String pass,
		String from, String to,
		String subject, String body) throws Exception {
        System.out.println("[Gmail][SMTP] 연결 시도 → " + SMTP_HOST + ":" + SMTP_PORT);
        System.out.println("[Gmail][SMTP] user=" + user + " from=" + from + " to=" + to);
        System.out.println("[Gmail][SMTP] subject=" + subject);
        // 1) 평문 소켓으로 연결
        java.net.Socket sock = new java.net.Socket(SMTP_HOST, SMTP_PORT);
        sock.setSoTimeout(15000);
        java.io.BufferedReader  rd = new java.io.BufferedReader(
		new java.io.InputStreamReader(sock.getInputStream(),  "UTF-8"));
        java.io.PrintWriter     wr = new java.io.PrintWriter(
		new java.io.OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
        // SMTP 헬퍼 (응답 읽기)
        java.util.function.Supplier<String> readLine = () -> {
            try { return rd.readLine(); } catch (Exception e) { return ""; }
		};
        java.util.function.Consumer<String> send = cmd -> wr.println(cmd);
        smtpExpect(readLine.get(), "220");              // 서버 인사
        System.out.println("[Gmail][SMTP] 220 서버 인사 OK");
        send.accept("EHLO localhost");
        String line;
        while ((line = readLine.get()) != null) {       // EHLO 멀티라인
            if (line.startsWith("250 ")) break;
		}
        System.out.println("[Gmail][SMTP] EHLO OK");
        // 2) STARTTLS 업그레이드
        send.accept("STARTTLS");
        smtpExpect(readLine.get(), "220");
        System.out.println("[Gmail][SMTP] STARTTLS OK");
        javax.net.ssl.SSLSocketFactory sf =
		(javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        javax.net.ssl.SSLSocket ssl =
		(javax.net.ssl.SSLSocket) sf.createSocket(sock, SMTP_HOST, SMTP_PORT, true);
        ssl.startHandshake();
        java.io.BufferedReader  srd = new java.io.BufferedReader(
		new java.io.InputStreamReader(ssl.getInputStream(),  "UTF-8"));
        java.io.PrintWriter     swr = new java.io.PrintWriter(
		new java.io.OutputStreamWriter(ssl.getOutputStream(), "UTF-8"), true);
        java.util.function.Supplier<String> sRead = () -> {
            try { return srd.readLine(); } catch (Exception e) { return ""; }
		};
        swr.println("EHLO localhost");
        while ((line = sRead.get()) != null) {
            if (line.startsWith("250 ")) break;
		}
        System.out.println("[Gmail][SMTP] SSL EHLO OK");
        // 3) AUTH LOGIN
        swr.println("AUTH LOGIN");
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(user.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(pass.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "235");                 // 인증 성공
        System.out.println("[Gmail][SMTP] AUTH LOGIN 인증 성공");
        // 4) 메일 전송
        swr.println("MAIL FROM:<" + from + ">");
        smtpExpect(sRead.get(), "250");
        System.out.println("[Gmail][SMTP] MAIL FROM OK");
        swr.println("RCPT TO:<" + to + ">");
        smtpExpect(sRead.get(), "250");
        System.out.println("[Gmail][SMTP] RCPT TO OK");
        swr.println("DATA");
        smtpExpect(sRead.get(), "354");
        System.out.println("[Gmail][SMTP] DATA 시작");
        // RFC 2047 Subject 인코딩 (한글 깨짐 방지)
        String encSubj = "=?UTF-8?B?" +
		java.util.Base64.getEncoder().encodeToString(subject.getBytes("UTF-8")) + "?=";
        String date = new java.text.SimpleDateFormat(
		"EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
		.format(new java.util.Date());
        swr.println("Date: " + date);
        swr.println("From: " + from);
        swr.println("To: " + to);
        swr.println("Subject: " + encSubj);
        swr.println("MIME-Version: 1.0");
        swr.println("Content-Type: text/plain; charset=UTF-8");
        swr.println("Content-Transfer-Encoding: base64");
        swr.println();
        // body를 Base64로 인코딩 (한글 완벽 지원)
        swr.println(java.util.Base64.getMimeEncoder(76, new byte[]{'\r','\n'})
		.encodeToString(body.getBytes("UTF-8")));
        swr.println(".");
        smtpExpect(sRead.get(), "250");                 // 전송 완료
        System.out.println("[Gmail][SMTP] 전송 완료 250 OK → " + to);
        swr.println("QUIT");
        ssl.close();
        sock.close();
	}
    private void smtpExpect(String response, String code) throws Exception {
        if (response == null || !response.startsWith(code))
		throw new Exception("SMTP 오류: " + response);
	}
    // ── 개발자 고정 계정 (XOR + Base64 난독화) ───────────────────
    //
    //  DEV_ID_ENC / DEV_PASS_ENC 값은
    //  DevCredentialEncryptor.java 를 로컬 1회 실행 후 교체한다.
    //  실행 후 DevCredentialEncryptor.java 는 즉시 삭제할 것.
    private static final int[] _K = {
        0x4B, 0x6F, 0x6F, 0x74,   // "Koot"
        0x50, 0x61, 0x6E, 0x4B,   // "PanK"
        0x69, 0x6E, 0x67, 0x32,   // "ing2"
        0x30, 0x32, 0x35, 0x21    // "025!"
	};
    /** 개발자 Gmail 주소 — DevCredentialEncryptor 로 생성 */
    private static final String DEV_ID_ENC   = "KgEOGD8GDScGDQxBVV1ATQsIAhU5DUAoBgM=";  // ← 교체
    /** 개발자 Gmail 앱 비밀번호 — DevCredentialEncryptor 로 생성 */
    private static final String DEV_PASS_ENC = "LRgJFzsDACoeBghfX11WVw==";  // ← 교체
    static String devGmailId()   { return xorDecrypt(DEV_ID_ENC);   }
    static String devGmailPass() { return xorDecrypt(DEV_PASS_ENC); }
    private static byte[] xorKey() {
        byte[] k = new byte[_K.length];
        for (int i = 0; i < _K.length; i++) k[i] = (byte) _K[i];
        return k;
	}
    private static String xorDecrypt(String b64) {
        try {
            byte[] key = xorKey();
            byte[] enc = java.util.Base64.getDecoder().decode(b64);
            byte[] out = new byte[enc.length];
            for (int i = 0; i < enc.length; i++)
			out[i] = (byte)(enc[i] ^ key[i % key.length]);
            return new String(out, "UTF-8");
		} catch (Exception e) { return ""; }
	}
	/*
		String err = sendOneSmtpWithAttachment(
		"abc@gmail.com",
		"앱비밀번호",
		"target@gmail.com",
		"첨부 테스트",
		"본문입니다.",
		new File("C:\\temp\\test.txt")
		);
		if (!err.isEmpty()) {    System.out.println(err);}
	*/
	private String getMimeType(java.io.File file) {
		if (file == null) return "application/octet-stream";
		try {
			String type = java.nio.file.Files.probeContentType(file.toPath());
			if (type != null && !type.trim().isEmpty()) {
				return type;
			}
		} catch (Exception ignored) {}
		String name = file.getName().toLowerCase();
		if (name.endsWith(".txt"))  return "text/plain";
		if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
		if (name.endsWith(".csv"))  return "text/csv";
		if (name.endsWith(".json")) return "application/json";
		if (name.endsWith(".xml"))  return "application/xml";
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
		if (name.endsWith(".png"))  return "image/png";
		if (name.endsWith(".gif"))  return "image/gif";
		if (name.endsWith(".bmp"))  return "image/bmp";
		if (name.endsWith(".webp")) return "image/webp";
		if (name.endsWith(".pdf"))  return "application/pdf";
		if (name.endsWith(".zip"))  return "application/zip";
		if (name.endsWith(".rar"))  return "application/x-rar-compressed";
		if (name.endsWith(".7z"))   return "application/x-7z-compressed";
		if (name.endsWith(".mp4"))  return "video/mp4";
		if (name.endsWith(".avi"))  return "video/x-msvideo";
		if (name.endsWith(".mkv"))  return "video/x-matroska";
		if (name.endsWith(".mov"))  return "video/quicktime";
		if (name.endsWith(".mp3"))  return "audio/mpeg";
		if (name.endsWith(".wav"))  return "audio/wav";
		if (name.endsWith(".ogg"))  return "audio/ogg";
		if (name.endsWith(".m4a"))  return "audio/mp4";
		if (name.endsWith(".doc"))  return "application/msword";
		if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		if (name.endsWith(".xls"))  return "application/vnd.ms-excel";
		if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		if (name.endsWith(".ppt"))  return "application/vnd.ms-powerpoint";
		if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
		return "application/octet-stream";
	}
	public String sendOneSmtpWithAttachment(
		String from,
		String pass,
		String to,
		String subject,
		String body,
		java.io.File attachment
		) {
		if (from == null || from.trim().isEmpty()) {
			return "[Gmail][attach] 실패 [발신자 ID 미확인]";
		}
		if (pass == null || pass.trim().isEmpty()) {
			return "[Gmail][attach] 실패 [발신자 앱 비밀번호 미확인]";
		}
		if (to == null || to.trim().isEmpty()) {
			return "[Gmail][attach] 실패 [수신자 ID 미확인]";
		}
		if (subject == null) subject = "";
		if (body == null) body = "";
		try {
			System.out.println("[Gmail][attach] 연결 시도 → " + SMTP_HOST + ":" + SMTP_PORT);
			System.out.println("[Gmail][attach] from=" + from + " to=" + to);
			System.out.println("[Gmail][attach] subject=" + subject);
			java.net.Socket sock = new java.net.Socket(SMTP_HOST, SMTP_PORT);
			sock.setSoTimeout(15000);
			java.io.BufferedReader rd = new java.io.BufferedReader(
				new java.io.InputStreamReader(sock.getInputStream(), "UTF-8")
			);
			java.io.PrintWriter wr = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true
			);
			java.util.function.Supplier<String> readLine = () -> {
				try { return rd.readLine(); }
				catch (Exception e) { return ""; }
			};
			smtpExpect(readLine.get(), "220");
			wr.println("EHLO localhost");
			String line;
			while ((line = readLine.get()) != null) {
				if (line.startsWith("250 ")) break;
			}
			wr.println("STARTTLS");
			smtpExpect(readLine.get(), "220");
			javax.net.ssl.SSLSocketFactory sf =
            (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
			javax.net.ssl.SSLSocket ssl =
            (javax.net.ssl.SSLSocket) sf.createSocket(sock, SMTP_HOST, SMTP_PORT, true);
			ssl.startHandshake();
			java.io.BufferedReader srd = new java.io.BufferedReader(
				new java.io.InputStreamReader(ssl.getInputStream(), "UTF-8")
			);
			java.io.PrintWriter swr = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(ssl.getOutputStream(), "UTF-8"), true
			);
			java.util.function.Supplier<String> sRead = () -> {
				try { return srd.readLine(); }
				catch (Exception e) { return ""; }
			};
			swr.println("EHLO localhost");
			while ((line = sRead.get()) != null) {
				if (line.startsWith("250 ")) break;
			}
			swr.println("AUTH LOGIN");
			smtpExpect(sRead.get(), "334");
			swr.println(java.util.Base64.getEncoder().encodeToString(from.getBytes("UTF-8")));
			smtpExpect(sRead.get(), "334");
			swr.println(java.util.Base64.getEncoder().encodeToString(pass.getBytes("UTF-8")));
			smtpExpect(sRead.get(), "235");
			swr.println("MAIL FROM:<" + from + ">");
			smtpExpect(sRead.get(), "250");
			swr.println("RCPT TO:<" + to + ">");
			smtpExpect(sRead.get(), "250");
			swr.println("DATA");
			smtpExpect(sRead.get(), "354");
			String encSubj = "=?UTF-8?B?"
            + java.util.Base64.getEncoder().encodeToString(subject.getBytes("UTF-8"))
            + "?=";
			String date = new java.text.SimpleDateFormat(
				"EEE, dd MMM yyyy HH:mm:ss Z",
				java.util.Locale.ENGLISH
			).format(new java.util.Date());
			swr.println("Date: " + date);
			swr.println("From: " + from);
			swr.println("To: " + to);
			swr.println("Subject: " + encSubj);
			swr.println("MIME-Version: 1.0");
			if (attachment != null && attachment.exists() && attachment.isFile()) {
				String boundary = "----=_Part_" + System.currentTimeMillis();
				String fileName = attachment.getName()
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_");
				String mimeType = getMimeType(attachment);
				String encodedFileName = "=?UTF-8?B?"
                + java.util.Base64.getEncoder().encodeToString(fileName.getBytes("UTF-8"))
                + "?=";
				byte[] fileBytes = java.nio.file.Files.readAllBytes(attachment.toPath());
				String fileBase64 = java.util.Base64
                .getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(fileBytes);
				String bodyBase64 = java.util.Base64
                .getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(body.getBytes("UTF-8"));
				swr.println("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"");
				swr.println();
				swr.println("--" + boundary);
				swr.println("Content-Type: text/plain; charset=UTF-8");
				swr.println("Content-Transfer-Encoding: base64");
				swr.println();
				swr.println(bodyBase64);
				swr.println();
				swr.println("--" + boundary);
				swr.println("Content-Type: " + mimeType + "; name=\"" + encodedFileName + "\"");
				swr.println("Content-Transfer-Encoding: base64");
				swr.println("Content-Disposition: attachment; filename=\"" + encodedFileName + "\"");
				swr.println();
				swr.println(fileBase64);
				swr.println();
				swr.println("--" + boundary + "--");
				} else {
				swr.println("Content-Type: text/plain; charset=UTF-8");
				swr.println("Content-Transfer-Encoding: base64");
				swr.println();
				swr.println(
					java.util.Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                    .encodeToString(body.getBytes("UTF-8"))
				);
			}
			swr.println(".");
			smtpExpect(sRead.get(), "250");
			swr.println("QUIT");
			ssl.close();
			sock.close();
			System.out.println("[Gmail][attach] 발송 완료 → " + to);
			return "";
			} catch (Exception e) {
			System.out.println("[Gmail][attach] 실패: " + e.getMessage());
			return "[Gmail][attach] 실패: " + e.getMessage();
		}
	}
	public String sendOneSmtpWithAttachments(
    String from,
    String pass,
    String to,
    String subject,
    String body,
    java.util.List<java.io.File> attachments
) {
    if (from == null || from.trim().isEmpty()) return "from 없음";
    if (pass == null || pass.trim().isEmpty()) return "pass 없음";
    if (to == null || to.trim().isEmpty()) return "to 없음";
    if (subject == null) subject = "";
    if (body == null) body = "";
    try {
        java.net.Socket sock = new java.net.Socket(SMTP_HOST, SMTP_PORT);
        sock.setSoTimeout(15000);
        java.io.BufferedReader rd = new java.io.BufferedReader(
            new java.io.InputStreamReader(sock.getInputStream(), "UTF-8"));
        java.io.PrintWriter wr = new java.io.PrintWriter(
            new java.io.OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
        java.util.function.Supplier<String> read = () -> {
            try { return rd.readLine(); } catch (Exception e) { return ""; }
        };
        smtpExpect(read.get(), "220");
        wr.println("EHLO localhost");
        while (!read.get().startsWith("250 ")) {}
        wr.println("STARTTLS");
        smtpExpect(read.get(), "220");
        javax.net.ssl.SSLSocketFactory sf =
            (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        javax.net.ssl.SSLSocket ssl =
            (javax.net.ssl.SSLSocket) sf.createSocket(sock, SMTP_HOST, SMTP_PORT, true);
        ssl.startHandshake();
        java.io.BufferedReader srd = new java.io.BufferedReader(
            new java.io.InputStreamReader(ssl.getInputStream(), "UTF-8"));
        java.io.PrintWriter swr = new java.io.PrintWriter(
            new java.io.OutputStreamWriter(ssl.getOutputStream(), "UTF-8"), true);
        java.util.function.Supplier<String> sRead = () -> {
            try { return srd.readLine(); } catch (Exception e) { return ""; }
        };
        swr.println("EHLO localhost");
        while (!sRead.get().startsWith("250 ")) {}
        swr.println("AUTH LOGIN");
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(from.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(pass.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "235");
        swr.println("MAIL FROM:<" + from + ">");
        smtpExpect(sRead.get(), "250");
        swr.println("RCPT TO:<" + to + ">");
        smtpExpect(sRead.get(), "250");
        swr.println("DATA");
        smtpExpect(sRead.get(), "354");
        String encSubj = "=?UTF-8?B?" +
            java.util.Base64.getEncoder().encodeToString(subject.getBytes("UTF-8")) + "?=";
        String boundary = "----=_Part_" + System.currentTimeMillis();
        swr.println("From: " + from);
        swr.println("To: " + to);
        swr.println("Subject: " + encSubj);
        swr.println("MIME-Version: 1.0");
        boolean hasFiles = attachments != null && !attachments.isEmpty();
        if (hasFiles) {
            swr.println("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"");
            swr.println();
            // ── 본문
            swr.println("--" + boundary);
            swr.println("Content-Type: text/plain; charset=UTF-8");
            swr.println("Content-Transfer-Encoding: base64");
            swr.println();
            swr.println(base64(body));
            swr.println();
            // ── 첨부파일들
            for (java.io.File f : attachments) {
                if (f == null || !f.exists()) continue;
                String fileName = f.getName();
                String mime = getMimeType(f);
                String encName = "=?UTF-8?B?" +
                    java.util.Base64.getEncoder().encodeToString(fileName.getBytes("UTF-8")) + "?=";
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                String fileB64 = java.util.Base64
                    .getMimeEncoder(76, new byte[]{'\r','\n'})
                    .encodeToString(bytes);
                swr.println("--" + boundary);
                swr.println("Content-Type: " + mime + "; name=\"" + encName + "\"");
                swr.println("Content-Transfer-Encoding: base64");
                swr.println("Content-Disposition: attachment; filename=\"" + encName + "\"");
                swr.println();
                swr.println(fileB64);
                swr.println();
            }
            swr.println("--" + boundary + "--");
        } else {
            // 파일 없으면 그냥 텍스트
            swr.println("Content-Type: text/plain; charset=UTF-8");
            swr.println("Content-Transfer-Encoding: base64");
            swr.println();
            swr.println(base64(body));
        }
        swr.println(".");
        smtpExpect(sRead.get(), "250");
        swr.println("QUIT");
        ssl.close();
        sock.close();
        return "";
    } catch (Exception e) {
        return "[attach] 실패: " + e.getMessage();
    }
}
	private String base64(String s) throws Exception {
	return java.util.Base64
        .getMimeEncoder(76, new byte[]{'\r','\n'})
        .encodeToString(s.getBytes("UTF-8"));
}
	// ── 유틸 ──────────────────────────────────────────────────────
	private static URL toUrl(String s) {
		try {
			return URI.create(s).toURL();
			} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
}