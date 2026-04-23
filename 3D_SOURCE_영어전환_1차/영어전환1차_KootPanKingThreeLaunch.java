import javafx.application.Application;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import lc.kra.system.mouse.GlobalMouseHook;
import lc.kra.system.mouse.event.GlobalMouseAdapter;
import lc.kra.system.mouse.event.GlobalMouseEvent;
import lc.kra.system.keyboard.GlobalKeyboardHook;
import lc.kra.system.keyboard.event.GlobalKeyAdapter;
import lc.kra.system.keyboard.event.GlobalKeyEvent;

public class KootPanKingThreeLaunch extends Application {
    // public static AppRestarter appRestarter;                // 재시작 / AppCDS Manage
	public static final GmailSender gmail = GmailSender.getInstance();
	public static TelegramBot tg;
    public static TOOLS.CaptureManager screenCapture;             // Theme 캡처
    public static TOOLS.CaptureManager.Camera camera = null;
    public static ChimeController chimeController;          // Chime
    public static GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    public static NaverCalendarService  naverCalendarService  = new NaverCalendarService();
	public static String startupScheduleText = "";
	public static KootPanKingThreeApp app ;
	public static Kakao kakao = new Kakao();
	public static MainWindow mainWindow = new MainWindow();;
	private static AppRestarter.ShutdownGuard shutdownGuard; // Force Shutdown Detected 훅
	private static GlobalMouseHook mouseHook;               // 글로벌 마우스 훅
	private static GlobalKeyboardHook keyboardHook;         // 글로벌 키보드 훅
	private static Stage primaryStageRef;                   // 시계 보이기/숨기기용
	public KootPanKingThreeLaunch () {
		System.out.println("[KootPanKingThreeLaunch ()]");
	};
	@Override
    public void start(Stage primaryStage) {
		System.out.println("■■■■■ start(Stage primaryStage)");
		primaryStageRef = primaryStage;
		
        java.util.List<String> rawArgs = getParameters().getRaw();
        String arg1 = rawArgs.size() > 0 ? rawArgs.get(0) : "default1";
        String arg2 = rawArgs.size() > 1 ? rawArgs.get(1) : "default2";
        String arg3 = rawArgs.size() > 2 ? rawArgs.get(2) : "default3";	
		
		System.out.println("arg1 = [" + arg1 + "]");
		System.out.println("arg2 = [" + arg2 + "]");
		System.out.println("arg3 = [" + arg3 + "]");
		
		showStartupScheduleTextLater();
		googleCalendarService.setSETTINGS_DIR(AppContext.SETTINGS_DIR);
        mainWindow.theMainWindow(primaryStage , arg1 , arg2 , arg3);
		telegramSetup();
		setupMouseHook();
		setupKeyboardHook();
	}
	private static void showStartupScheduleTextLater() {
		new Thread(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException ignored) {}
			int waitCount = 0;
			while (mainWindow == null && waitCount < 200) { // 최대 10s
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {}
				waitCount++;
			}
			if (mainWindow == null) {
				System.out.println("mainWindow initialization 안 됨");
				return;
			}
			// if (startupScheduleText != null && !startupScheduleText.isEmpty()) {
			javafx.application.Platform.runLater(() -> {
				System.out.println("정 통보 다이알로그 등록");
				firstFinalGmail();
				KakaoSetup();
				mainWindow.showScheduleDialog("📅 Next 3 days schedule", startupScheduleText);
				shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg); // Force Shutdown Detected 훅 Register
				shutdownGuard.register(); 
			});
			// }
		}, "StartupScheduleDelay").start();
	}
    private static void firstFinalGmail() {
		gmail.init();
		gmail.sendStartupNotice(startupScheduleText);
	}
	private static void telegramSetup() {
		googleCalendarService.setSETTINGS_DIR(AppContext.SETTINGS_DIR);
		tg = TelegramBot.getInstance(new TelegramBot.CommandHandler() {
			@Override public java.io.File captureClockScreen() throws Exception {
				if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
				return screenCapture.captureClockScreen();
			}
			@Override public java.io.File captureFullScreen() throws Exception {
				if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
				return screenCapture.captureFullScreen();
			}
			@Override public java.io.File captureMonitor(int i) throws Exception {
				if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
				return screenCapture.captureMonitor(i);
			}
			@Override public void shutdownPC() {
				System.out.println("=======텔레그램 원격 종료");
				// if (shutdownGuard != null) shutdownGuard.cancel();
				// saveConfig();
				String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
				gmail.sendShutdownNoticeSync(
					"Telegram Remote Shutdown Notice",
					GmailSender.APP_SIGNATURE + "PC is shutting down via Telegram command.\n\nTime: " + now
				);
				System.out.println("AppLogger.close");
				AppLogger.close();
				try {
					Runtime.getRuntime().exec(new String[]{"shutdown", "-s", "-f", "-t", "0"});
					} catch (Exception e) {
					System.out.println("[Shutdown] " + e.getMessage());
					AppLogger.logException(e);
				}
			}
			@Override public void rebootPC() {
				System.out.println("=======텔레그램 원격 재시작");
				// if (shutdownGuard != null) shutdownGuard.cancel();
				// saveConfig();
				String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
				gmail.sendShutdownNoticeSync(
					"Telegram Remote Reboot Notice",
					GmailSender.APP_SIGNATURE + "PC is rebooting via Telegram command.\n\nTime: " + now
				);
				System.out.println("AppLogger.close");
				AppLogger.close();
				try {
					Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "-f", "-t", "0"});
					} catch (Exception e) {
					System.out.println("[Reboot] " + e.getMessage());
				}
			}
			@Override public void showImage(java.io.File imageFile) {
				if (screenCapture != null) {
					screenCapture.showImageWindow(imageFile);
				}
			}
			@Override public void playMedia(java.io.File mediaFile) {
				if (chimeController != null) {
					chimeController.playMediaFile(mediaFile);
				}
			}
			@Override public void saveConfig() {
				// KootPanKingThreeApp.this.saveConfig();
			}
			@Override public void openTextFile(java.io.File file) {
				javafx.application.Platform.runLater(() -> {
					if (mainWindow != null) {
						mainWindow.showTheMainWindow();
						MainWindow.openTextFileWindow(file);
					}
				});
			}
		});
		
		tg.init();
		
		/*
			this.appRestarter = KootPanKingThree.getAppRestarter();
			if (this.appRestarter == null) {
            throw new IllegalStateException("AppRestarter not initialized in main()");
			}
			this.appRestarter.setTelegramBot(this.tg);
		*/
        // loadConfig();
        // this.gmail.exeFilePath = !EXE_PATH.isEmpty() ? EXE_PATH : AppLogger.getExeFilePath();
        // this.gmail.logFilePath = AppLogger.getLogFilePath();
		/*
			if (this.appRestarter != null) {
            this.appRestarter.setCachedPaths(
			config.getProperty("app.exePath", ""),
			config.getProperty("app.javawPath", ""),
			config.getProperty("app.jsaPath", "")
			);
			}
		*/
        
		screenCapture = new TOOLS.CaptureManager(null);
		
		tg.calendarService = googleCalendarService;
		tg.naverCalendarService = naverCalendarService;
		
		// ── 네이버 캘린더 자격증명 로드 ───────────────────────
		naverCalendarService.setCredentials(
			AppContext.get("naver.caldav.id", ""),
			AppContext.get("naver.caldav.password", "")
		);
		tg.naverCalendarService = naverCalendarService;
		
		new Thread(() -> {
            if (NaverCalendarService.credentialsExist(
			naverCalendarService.naverId, naverCalendarService.naverPassword)) {
			naverCalendarService.init();
            }
            if (googleCalendarService.credentialsExist()) {
				googleCalendarService.init();
			}
            //  if (tg.myChatId.isEmpty() || tg.botToken.isEmpty()) return;
            try {
				StringBuilder sb = new StringBuilder("📅 Next 3 days schedule\n\n");
				if (googleCalendarService.isInitialized()) {
					java.util.List<GoogleCalendarService.CalendarEvent> gEvents =
					googleCalendarService.getNextDays(3);
					sb.append(GoogleCalendarService.formatEvents("📧 Google 📧", gEvents)).append("\n\n\n");
				}
				if (naverCalendarService.isInitialized()) {
					java.util.List<NaverCalendarService.CalendarEvent> nEvents =
					naverCalendarService.getNextDays(3);
					sb.append(NaverCalendarService.formatEvents("🟢 Naver 🟢", nEvents));
				}
				startupScheduleText = sb.toString().trim();
				if (startupScheduleText.isEmpty()) return;
				tg.sendTelegram(startupScheduleText);
				// javafx.application.Platform.runLater(() ->	showScheduleDialog("📅 향후 3 정", startupScheduleText));
				} catch (Exception e) {
				System.out.println("[CalendarInit] 정 조회 Failed: " + e.getMessage());
				AppLogger.logException(e);
			}
		}, "CalendarInit").start();
		
        // this.shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg);
        // this.appRestarter.buildAppCdsIfNeeded(this::saveConfig);
        // this.shutdownGuard.register();
		
		tg.polling = true;
		tg.startPolling();
		tg.sendStartupNotice();
	}	
	
	private static void setupMouseHook() {
		try {
			mouseHook = new GlobalMouseHook();
			mouseHook.addMouseListener(new GlobalMouseAdapter() {
				@Override public void mousePressed(GlobalMouseEvent event) {
					boolean left  = (event.getButtons() & GlobalMouseEvent.BUTTON_LEFT)  != GlobalMouseEvent.BUTTON_NO;
					boolean right = (event.getButtons() & GlobalMouseEvent.BUTTON_RIGHT) != GlobalMouseEvent.BUTTON_NO;
					if (left && right) {
						Platform.runLater(() -> {
							// 시계: alwaysOnTop으로 맨 앞으로
							if (app != null && app.clockController != null) {
								Stage clockStage = app.clockController.getStage();
								if (clockStage != null) {
									if (!clockStage.isShowing()) clockStage.show();
									clockStage.setAlwaysOnTop(true);
									clockStage.toFront();
									clockStage.requestFocus();
									if (!app.alwaysOnTop)
										clockStage.setAlwaysOnTop(false);
									System.out.println("[MouseHook] 시계 맨 앞으로");
								}
							}
							// 메인창 토글 (이미 존재Other는 메서드 Enable)
							if (mainWindow != null)
								mainWindow.toggleTheMainWindow();
						});
					}
				}
			});
			System.out.println("[MouseHook] 글로벌 마우스 훅 시작");
		} catch (Exception e) {
			System.out.println("[MouseHook] 훅 initialization Failed: " + e.getMessage());
		}
	}

	private static void setupKeyboardHook() {
		try {
			keyboardHook = new GlobalKeyboardHook(false);
			keyboardHook.addKeyListener(new GlobalKeyAdapter() {
				@Override public void keyPressed(GlobalKeyEvent event) {
					int vk = event.getVirtualKeyCode();
					// 윈도우키(좌/우) → 마우스 버튼 2개와 동한 처리
					if (vk == GlobalKeyEvent.VK_LWIN || vk == GlobalKeyEvent.VK_RWIN) {
						Platform.runLater(() -> {
							// 시계: alwaysOnTop 트릭으로 맨 앞 (즉h 원복)
							if (app != null && app.clockController != null) {
								Stage clockStage = app.clockController.getStage();
								if (clockStage != null) {
									if (!clockStage.isShowing()) clockStage.show();
									clockStage.setAlwaysOnTop(true);
									clockStage.toFront();
									clockStage.requestFocus();
									clockStage.setAlwaysOnTop(false);
									System.out.println("[KeyboardHook] 시계 맨 앞으로");
								}
							}
							// 메인창 토글
							if (mainWindow != null)
								mainWindow.toggleTheMainWindow();
						});
					}
				}
			});
			System.out.println("[KeyboardHook] 글로벌 키보드 훅 시작");
		} catch (Exception e) {
			System.out.println("[KeyboardHook] 훅 initialization Failed: " + e.getMessage());
		}
	}

	private static void KakaoSetup() {
		tg.kakao = kakao;
		kakao.appDir = AppContext.getAPP_DIR();
		kakao.kakaoRestApiKey   = AppContext.get("kakao.apiKey", "");
		kakao.kakaoClientSecret = AppContext.get("kakao.clientSecret", "");
		kakao.kakaoRefreshToken = AppContext.get("kakao.refreshToken", "");
		
		// 필요Other면
		// kakao.onTokenSaved = KootPanKingThree::saveMainIni;
		
		new Thread(() -> {
            if (!kakao.kakaoRestApiKey.isEmpty()
				&& !kakao.kakaoClientSecret.isEmpty()
				&& !kakao.kakaoRefreshToken.isEmpty()) {
				try {	kakao.autoRefreshLogin();	} catch (Exception e) {
					System.out.println("[] Kakao Failed: " + e.getMessage());
					AppLogger.logException(e);	
				}
			}
            // gmail.sendStartupNotice();
            // tg.sendStartupNotice();
		}, "KakaoAutoLogin").start();
	}	
	
	public static void main(String[] args) {
		AppLogger.init();
		AppContext.init();
		/*
			firstFinalGmail();
			telegramSetup();
			KakaoSetup();
		*/
		try {
			System.out.println("[(Application.launch)-------000]");
			launch(args);
			System.out.println("[(Application.launch)-------999]");
			} finally {
			System.out.println("[Launcher] bye bye");
		}
		try {
			if (tg != null) {
				tg.stopPolling();   // 반드h 필요 (없으면 추가)
			}
		} catch (Exception ignored) {}
		try {
			if (mouseHook != null && mouseHook.isAlive()) {
				mouseHook.shutdownHook();
			}
		} catch (Exception ignored) {}
		try {
			if (keyboardHook != null && keyboardHook.isAlive()) {
				keyboardHook.shutdownHook();
			}
		} catch (Exception ignored) {}
		AppLogger.close();
	}
	
}