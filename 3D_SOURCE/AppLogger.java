/*
System.out.println 이 아닌 경우를 일단 찾아봐 전부 다


찾아보면, System.out.println(...) 말고도 실제로 쓰는 출력/기록 방식이 몇 가지 있다.
지금 업로드된 파일들 기준으로 정리하면 이렇다.

첫째, System.err.println(...) 와 e.printStackTrace(...)
AppLogger.init() 실패 처리에서 originalErr.println(...)를 직접 쓰고, 같은 구간에서 e.printStackTrace(originalErr)도 호출한다. 즉, 초기화 실패류는 표준 에러로 직접 보낸다.

둘째, java.util.logging.Logger 방식
FxGPUNeon에는 private static final Logger LOG = Logger.getLogger(...)가 있고, 배경 이미지 로드 실패 때 LOG.log(Level.WARNING, ...)를 쓴다. 즉 여기만큼은 System.out.println이 아니라 JDK 로거를 병행한다.

셋째, AppLogger 직접 기록 API
AppLogger.writeToFile(...), AppLogger.writeToFile(..., true)처럼 콘솔이 아니라 파일에 직접 쓰는 방식이 있다. 그리고 예외용으로 AppLogger.logException(e)도 곳곳에서 호출된다. 예를 들어 KootPanKingThree의 종료/초기화 예외 처리, FxGPUNeon의 다이얼로그 종료/이미지 처리 실패에서 이 방식이 쓰인다.

넷째, PrintWriter.println(...) 기반의 프로토콜 출력
GmailSender는 SMTP 통신용 PrintWriter(wr, swr)에 println(...)을 여러 번 호출한다. 이건 로그가 아니라 소켓으로 보내는 SMTP 명령 출력이다. 이름은 println이지만 콘솔 로그가 아니다.

다섯째, PrintWriter writer.println(...) 기반 로그 파일 기록
AppLogger 내부에서는 최종적으로 writer.println(...)으로 로그 파일에 직접 쓴다. 또한 TeeStream이 System.out/err를 가로채서 줄 단위로 writeToFile(...)에 넘긴다. 즉 실제 저장은 writer.println(...) 계열이다.

*/

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*  caller 추적 기능 삭제하려면 :
	java -Dapplogger.callerTracking=true -jar app.jar
*/

// ═══════════════════════════════════════════════════════════
//  AppLogger - 모든 콘솔 출력을 로그 파일에 동시 기록
//
//  로그 폴더 : <실행폴더>/log/
//  파일명    : <실행파일명>_yyyyMMdd_HHmmss.txt
//
//  개선 내역
//  B. System.err 별도 [ERR] 태그 분리
//     - out Tee 와 err Tee 를 독립적으로 구성
//     - err 경유 라인은 파일에 [ERR] 접두어 추가
//  C. 스레드명 포함
//     - 파일 기록 포맷: [타임스탬프] [스레드명] [Class#method] 메시지
//  G. resolveExePath() ② 경로 복구
//     - 원래 코드에서 주석 번호가 ①→③ 으로 건너뛰던 누락 경로
//       (ProcessHandle 기반 실행 명령행 탐색) 추가
//  H. close() 미기록 버퍼 플러시 보강
//     - lineBuf 에 남아있는 미완성 라인을 close() 시 강제 기록
//  신규. 호출자 자동 감지
//     - StackTrace 를 분석해 AppLogger/PrintStream/java.* 를 건너뛰고
//       실제 호출한 앱 클래스·메쏘드를 [Class#method] 형태로 선두에 삽입
//
//  추가 보완
//  I. caller 추적 on/off 옵션화
//     - JVM 옵션: -Dapplogger.callerTracking=true
//     - false 이면 resolveCallerTag() 를 호출하지 않아 StackTrace 비용 절감
//  J. DateTimeFormatter 사용
//     - 기존 SimpleDateFormat static 공유는 멀티스레드에서 안전하지 않으므로 교체
//  K. init() 중복 호출 방지
//     - 이미 초기화된 상태에서 다시 setOut/setErr 하지 않도록 보호
//  L. close() 시 원본 System.out / System.err 복구
//     - 로거 종료 후 표준 출력 스트림을 원래대로 되돌림
// ═══════════════════════════════════════════════════════════
public class AppLogger {
	
    private static PrintWriter  writer      = null;
    private static String       logFilePath = "";
    private static String       exeFilePath = "";
    private static final Object LOCK        = new Object();
    private static final Object INIT_LOCK   = new Object();
	
    // J. thread-safe 포맷터로 교체
    private static final DateTimeFormatter TS =
	DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS =
	DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	
    // I. 호출자 추적 on/off (기본 false)
    //    사용 예: java -Dapplogger.callerTracking=true -jar app.jar
    private static volatile boolean callerTrackingEnabled =
	Boolean.parseBoolean(System.getProperty("applogger.callerTracking", "false"));
	
    // K. init() 중복 호출 방지
    private static volatile boolean initialized = false;
	
    // lineBuf 는 out/err 각각 독립 관리 (내부 Tee 클래스에서 직접 보유)
    // close() 시 플러시를 위해 두 Tee 의 참조를 보관
    private static TeeStream outTee = null;
    private static TeeStream errTee = null;
	
    // L. close() 시 원복할 원본 스트림
    private static PrintStream originalOut = System.out;
    private static PrintStream originalErr = System.err;
	
    // ── 공개 API ─────────────────────────────────────────────────
	
    /** 로거 초기화 - main() 가장 먼저 호출 */
    public static void init() {
		
        synchronized (INIT_LOCK) {
            if (initialized) return;
			
            originalOut = System.out;
            originalErr = System.err;
			
            // ① sun.java.command / ProcessHandle / CodeSource 순으로 실행 파일 경로 탐색
            String exePath = resolveExePath();
            exeFilePath = exePath != null ? exePath : "(unknown)";
			
            // 실행 파일 정보 (baseName 용도로만 사용)
            File exeFile = exePath != null ? new File(exePath) : null;
			
            // log 폴더: %APPDATA%\KootPanKingThree\log\
            // 재설치 시 삭제되지 않도록 실행 폴더 대신 APPDATA 아래에 고정
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.trim().isEmpty()) {
                appData = System.getProperty("user.home");
			}
            File logDir = new File(appData + File.separator
			+ "KootPanKingThree" + File.separator + "log");
            if (!logDir.exists()) logDir.mkdirs();
			
            // 로그 파일명: <실행파일 기본명>_yyyyMMdd_HHmmss.txt
            String baseName  = (exeFile != null) ? stripExt(exeFile.getName()) : "KootPanKingThree";
            String timestamp = FILE_TS.format(LocalDateTime.now());
            String fileName  = baseName + "_" + timestamp + ".txt";
            File   logFile   = new File(logDir, fileName);
            logFilePath = logFile.getAbsolutePath();
			
            // PrintWriter 열기 (UTF-8, 자동 flush)
            try {
                writer = new PrintWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(logFile, true),
                        StandardCharsets.UTF_8
					),
                    true
				);
				} catch (Exception e) {
                originalErr.println("[AppLogger] 로그 파일 열기 실패: " + e.getMessage());
                e.printStackTrace(originalErr);
                return;
			}
			
            // ── B. out / err 를 별도 TeeStream 으로 교체 ──────────────
            final PrintStream prevOut = System.out;
            final PrintStream prevErr = System.err;
			
            try {
                outTee = new TeeStream(prevOut, false);  // isErr = false
                errTee = new TeeStream(prevErr, true);   // isErr = true  → [ERR] 접두어
				} catch (Exception e) {
                originalErr.println("[AppLogger] tee 스트림 생성 실패: " + e.getMessage());
                e.printStackTrace(originalErr);
                writer.close();
                writer = null;
                return;
			}
			
            System.setOut(outTee);
            System.setErr(errTee);
            initialized = true;
			
            System.out.println("[AppLogger] 초기화 완료");
            System.out.println("[AppLogger] callerTracking=" + callerTrackingEnabled
			+ " (옵션: -Dapplogger.callerTracking=true)");
            System.out.println("[AppLogger] 실행 파일: " + exeFilePath);
            System.out.println("[AppLogger] 로그 파일: " + logFilePath);
		}
	}
	
    /** caller 추적 on/off 수동 제어 */
    public static void setCallerTrackingEnabled(boolean enabled) {
        callerTrackingEnabled = enabled;
	}
	
    /** caller 추적 활성 여부 반환 */
    public static boolean isCallerTrackingEnabled() {
        return callerTrackingEnabled;
	}
	
    /** writer 에만 직접 기록 (타임스탬프 + 스레드명 + 호출자 포함) */
    public static void writeToFile(String msg) {
        writeToFile(msg, false);
	}
	
    /** writer 에만 직접 기록 - isErr=true 이면 [ERR] 접두어 추가 */
    public static void writeToFile(String msg, boolean isErr) {
        if (writer == null || msg == null) return;
		
        String ts     = TS.format(LocalDateTime.now());
        String thread = Thread.currentThread().getName();
        String caller = callerTrackingEnabled ? resolveCallerTag() : "";
        String prefix = isErr ? "[ERR] " : "";
		
        synchronized (LOCK) {
            if (callerTrackingEnabled) {
                writer.println("[" + ts + "] [" + thread + "] " + caller + prefix + msg);
				} else {
                writer.println("[" + ts + "] [" + thread + "] " + prefix + msg);
			}
		}
	}
	
    /** 로그 파일 전체 경로 반환 */
    public static String getLogFilePath() { return logFilePath; }
	
    /** 실행 파일 전체 경로 반환 */
    public static String getExeFilePath() { return exeFilePath; }
	
    /**
		* 로거 닫기.
		* H. close() 시 out/err lineBuf 에 남아있는 미완성 라인을 강제 플러시한 뒤
		*    writer 를 닫는다.
		* L. 표준 출력 스트림을 원래대로 복구한다.
	*/
    public static void close() {
        synchronized (INIT_LOCK) {
            // 미기록 버퍼 플러시 (개행 없이 프로세스가 종료될 때 마지막 라인 손실 방지)
            try {
                if (outTee != null) outTee.flushLineBuf();
                if (errTee != null) errTee.flushLineBuf();
				} catch (Exception e) {
                originalErr.println("[AppLogger] close() flush 실패: " + e.getMessage());
			}
			
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
				}
				} catch (Exception e) {
                originalErr.println("[AppLogger] writer close 실패: " + e.getMessage());
			}
			
            // L. 원본 스트림 복구
            try { if (originalOut != null) System.setOut(originalOut); } catch (Exception ignored) {}
            try { if (originalErr != null) System.setErr(originalErr); } catch (Exception ignored) {}
			
            writer = null;
            outTee = null;
            errTee = null;
            initialized = false;
		}
	}
	
    // ── 내부: TeeStream ──────────────────────────────────────────
	
    /**
		* out 또는 err 를 감싸는 Tee 스트림.
		* - 콘솔(원본 스트림)에는 그대로 전달
		* - 파일에는 타임스탬프 + 스레드명 + 호출자 + (ERR 태그) 를 앞에 붙여 기록
		* - isErr=true 이면 파일 기록 시 [ERR] 접두어 추가
	*/
    private static class TeeStream extends PrintStream {
        private final StringBuilder lineBuf = new StringBuilder();
        private final boolean isErr;
		
        TeeStream(PrintStream original, boolean isErr) throws UnsupportedEncodingException {
            super(original, true, "UTF-8");
            this.isErr = isErr;
		}
		
        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);   // 콘솔 출력
            String s;
            try {
                s = new String(buf, off, len, StandardCharsets.UTF_8);
				} catch (Exception e) {
                s = new String(buf, off, len);
			}
			
            synchronized (lineBuf) {
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '\n') {
                        flushCurrentLine();
						} else if (c != '\r') {
                        lineBuf.append(c);
					}
				}
			}
		}
		
        // K-보완. print(char) / write(int) 경로도 안전하게 흡수
        @Override
        public void write(int b) {
            super.write(b);   // 콘솔 출력
            char c = (char) (b & 0xFF);
            synchronized (lineBuf) {
                if (c == '\n') {
                    flushCurrentLine();
					} else if (c != '\r') {
                    lineBuf.append(c);
				}
			}
		}
		
        /** 내부 lineBuf 의 현재 내용을 한 줄로 파일 기록 */
        private void flushCurrentLine() {
            String line = lineBuf.toString();
            lineBuf.setLength(0);
            if (!isSuppressed(line)) writeToFile(line, isErr);
		}
		
        /** H. close() 시 미완성 라인 강제 기록 */
        void flushLineBuf() {
            synchronized (lineBuf) {
                if (lineBuf.length() > 0) {
                    flushCurrentLine();
				}
			}
		}
	}
	
    // ── 내부: 노이즈 필터 ────────────────────────────────────────
	
    /**
		* 로그 파일에 기록하지 않을 노이즈 라인 판정.
		* - [Stream] ... 캡처 완료
		* - [Telegram Poll] ok:true result:[]
	*/
	private static boolean isSuppressed(String msg) {
		if (msg == null) return false;
		String s = msg.trim();
		if (s.isEmpty()) return false;
		// ── Stream ─────────────────────────────
		if (s.contains("[Stream]") && s.contains("캡처 완료")) return true;
		// ── Telegram ───────────────────────────
		if (s.contains("[Telegram Poll]")
			&& s.contains("\"ok\":true")
		&& s.contains("\"result\":[]")) return true;
		// ── JavaFX D3D ─────────────────────────
		if (s.contains("D3D Vram Pool") && s.contains("Growing pool")) return true;
		return false;
	}
	/*
		// ── JavaFX / Prism / D3D 초기화 로그 ───
		if (s.contains("Prism pipeline init order:")) return true;
		if (s.contains("Using Double Precision Marlin Rasterizer")) return true;
		if (s.contains("Using dirty region optimizations")) return true;
		if (s.contains("Not using texture mask for primitives")) return true;
		if (s.contains("Not forcing power of 2 sizes for textures")) return true;
		if (s.contains("Using hardware CLAMP_TO_ZERO mode")) return true;
		if (s.contains("Opting in for HiDPI pixel scaling")) return true;
		if (s.contains("Prism pipeline name =")) return true;
		if (s.contains("Loading D3D native library")) return true;
		if (s.equals("succeeded.")) return true;
		if (s.contains("Direct3D initialization succeeded")) return true;
		if (s.contains("Initialized prism pipeline:")) return true;
		if (s.contains("(X) Got class = class com.sun.prism.d3d.D3DPipeline")) return true;
		
		// ── JavaFX / D3D 정보 로그 ─────────────
		if (s.contains("Maximum supported texture size:")) return true;
		if (s.contains("Maximum texture size clamped to")) return true;
		if (s.equals("OS Information:")) return true;
		if (s.contains("Windows version ")) return true;
		if (s.equals("D3D Driver Information:")) return true;
		if (s.contains("Intel(R) UHD Graphics")) return true;
		if (s.contains("\\\\.\\DISPLAY")) return true;
		if (s.contains("Driver igdumdim64.dll")) return true;
		if (s.contains("Pixel Shader version")) return true;
		if (s.contains("Device : ven_")) return true;
		if (s.contains("Max Multisamples supported:")) return true;
		if (s.contains("vsync: true vpipe: true")) return true;
		
		// ── JavaFX 렌더러 내부 반복 로그 ───────
		if (s.contains("new alphas with length =")) return true;
		if (s.contains("PPSRenderer: scenario.effect - createShader:")) return true;
		if (s.contains("Growing pool D3D Vram Pool")) return true;
		
	*/	
    // ── 내부: 호출자 태그 ────────────────────────────────────────
	
    /**
		* C / 신규. StackTrace 를 분석해 실제 호출한 앱 클래스·메쏘드를 반환.
		*
		* 건너뛸 프레임:
		*   - java.*, sun.*, javax.* 패키지
		*   - AppLogger 자신
		*   - TeeStream (AppLogger$TeeStream 형태)
		*
		* 반환 형식: [ClassName#methodName]
		* 탐색 실패 시 빈 문자열 반환.
	*/
    private static String resolveCallerTag() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
			
            // JVM 내부 / 로거 자신 / Tee 내부 클래스 제외
            if (cls.startsWith("java.")
                || cls.startsWith("sun.")
                || cls.startsWith("javax.")
                || cls.startsWith("jdk.")
                || cls.equals("AppLogger")
                || cls.startsWith("AppLogger$")) {
                continue;
			}
			
            // 패키지 제거 후 단순 클래스명만 사용
            String simpleCls = cls;
            int dot = simpleCls.lastIndexOf('.');
            if (dot >= 0) simpleCls = simpleCls.substring(dot + 1);
			
            // 익명 클래스($1, $2 …)는 외부 클래스명만 사용
            if (simpleCls.contains("$")) {
                simpleCls = simpleCls.substring(0, simpleCls.indexOf('$'));
			}
			
            return "[" + simpleCls + "#" + e.getMethodName() + "] ";
		}
        return "";
	}
	
    // ── 내부: 실행 파일 경로 탐색 ────────────────────────────────
	
    /**
		* G. 실행 파일 경로를 3단계로 탐색.
		*
		* ① sun.java.command 시스템 프로퍼티
		*    - java -jar app.jar  또는 launch4j exe 로 실행 시 파일명이 포함됨
		* ② CodeSource (getProtectionDomain)
		*    - IDE / class 직접 실행 시 사용
		* ③ ProcessHandle (Java 9+) 로 현재 프로세스 명령행 파싱
		*    - ①, ② 가 실패했을 때 최후 수단
	*/
    private static String resolveExePath() {
		
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
                    File exeCandidate = new File(parent, "KootPanKingThree.exe");
                    if (exeCandidate.exists()) return exeCandidate.getAbsolutePath();
				}
                return jarFile.getAbsolutePath();
			}
		} catch (Exception ignored) {}
		
        // ② CodeSource (JAR / class 실행)
        try {
            java.security.CodeSource cs =
			AppLogger.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                File f = new File(cs.getLocation().toURI()).getAbsoluteFile();
                String name = f.getName().toLowerCase();
				
                if (name.equals("java.exe") || name.equals("javaw.exe")
					|| name.equals("java")     || name.equals("javaw")) {
                    // java/javaw → 건너뜀, ProcessHandle ③ 에서 처리
					} else if (f.isDirectory()) {
                    // IDE/class 직접 실행: 디렉터리 안에 exe 있는지 탐색
                    File exeCandidate = new File(f, "KootPanKingThree.exe");
                    if (exeCandidate.exists()) return exeCandidate.getAbsolutePath();
					} else if (name.endsWith(".jar")) {
                    // jar 옆에 exe 있으면 exe 우선
                    File parent = f.getParentFile();
                    if (parent != null) {
                        File exeCandidate = new File(parent, "KootPanKingThree.exe");
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
	public static void logException(Throwable t) {
		System.err.println("[EXCEPTION] " + t);
		t.printStackTrace();
	}
    // ── 내부: 유틸 ───────────────────────────────────────────────
	
    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
	}
}
