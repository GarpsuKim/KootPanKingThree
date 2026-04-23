import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.io.*;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;

	import javafx.collections.FXCollections;
	import javafx.collections.ObservableList;
	import javafx.geometry.Insets;
	import javafx.geometry.Pos;
	import javafx.scene.Scene;
	import javafx.scene.control.*;
	import javafx.scene.input.KeyCode;
	import javafx.scene.layout.*;
	import javafx.scene.text.Font;
	import javafx.stage.*;
	
	import java.io.*;
	import java.text.SimpleDateFormat;
	import java.util.*;
	import java.util.concurrent.atomic.AtomicBoolean;

/**
	* BackgroundPlayer — 배경 재생 관련 기능 모음
	*
	* ┌─────────────────────────────────────────────────────┐
	* │  BackgroundPlayer.YoutubePlayer                     │
	* │    YouTube / 라이브 스트림 배경 재생                  │
	* │    yt-dlp → ffmpeg 30초 청크 → JavaFX MediaPlayer   │
	* ├─────────────────────────────────────────────────────┤
	* │  BackgroundPlayer.CalendarAlarmPoller               │
	* │    Google + 네이버 캘린더 이벤트 폴링 및 알림          │
	* └─────────────────────────────────────────────────────┘
*/
public class BackgroundPlayer {

	/**
		* SuperDir — 디렉터리 재귀 탐색 뷰어 (JavaFX 버전)
		*
		* ── 기능 ────────────────────────────────────────────────────────
		*   • 확장자 필터: 전체 / 텍스트 / 이미지 / 음악 / 동영상 / 문서 / 기타
		*   • 목록에서 텍스트 파일 선택 후 Enter 또는 더블클릭 → 자체 뷰어 오픈
		*   • F5: 스캔  /  ESC: 중지  /  Ctrl+S: 저장
		*   • 싱글턴 (open() 로 호출)
	*/
	public static class SuperDir {
		// ── 싱글턴 ──────────────────────────────────────────────────
		private static SuperDir instance = null;
		public static void open(Window owner) {
			if (instance == null || instance.stage == null || !instance.stage.isShowing()) {
				instance = new SuperDir();
				instance.show(owner);
				} else {
				instance.stage.show();
				instance.stage.toFront();
				instance.stage.requestFocus();
			}
		}
		// ── 색상 상수 ────────────────────────────────────────────────
		private static final String BG_COLOR   = "#EBF5FF";
		private static final String FG_COLOR   = "#14325A";
		private static final String HEADER_BG  = "#ADD8E6";
		private static final String BORDER_CLR = "#78AAC8";
		private static final String STATUS_BG  = "#C8E1F5";
		private static final String STATUS_FG  = "#143C78";
		private static final String DIR_COLOR  = "#1450B4";
		private static final String MATCH_BG   = "#FFFFD2";
		private static final String SEL_BG     = "#B4D2F0";
		private static final String BTN_BG     = "#D2EBF8";
		
		// ── 스캔 상수 ────────────────────────────────────────────────
		private static final int BATCH_SIZE = 1000;
		
		// ── 확장자 필터 ──────────────────────────────────────────────
		private enum ExtFilter {
			TEXT  ("텍스트", new String[]{"txt","log","ini","java","py","js","ts","css","html","htm",
			"xml","json","md","csv","properties","sh","bat","c","cpp","h","yaml","toml"}),
			IMAGE ("이미지", new String[]{"jpg","jpeg","png","gif","bmp","webp","svg","ico","tif","tiff","raw"}),
			MUSIC ("음악",   new String[]{"mp3","wav","flac","aac","ogg","wma","m4a","opus","mid","midi"}),
			VIDEO ("동영상", new String[]{"mp4","avi","mkv","mov","wmv","flv","webm","m4v","ts","mpg","mpeg","3gp"}),
			DOC   ("문서",   new String[]{"pdf","xls","xlsx","doc","docx","ppt","pptx","hwp","hwpx","odt","ods","odp"}),
			ALL   ("전체",   null),
			OTHER ("기타",   new String[0]);
			
			final String label;
			final String[] exts;
			
			ExtFilter(String label, String[] exts) { this.label = label; this.exts = exts; }
			
			boolean matches(String fileName) {
				if (this == ALL) return true;
				String lower = fileName.toLowerCase();
				int dot = lower.lastIndexOf('.');
				if (dot < 0) return this == OTHER;
				String ext = lower.substring(dot + 1);
				if (this == OTHER) {
					for (ExtFilter f : values()) {
						if (f == ALL || f == OTHER) continue;
						for (String e : f.exts) if (e.equals(ext)) return false;
					}
					return true;
				}
				for (String e : exts) if (e.equals(ext)) return true;
				return false;
			}
			
			static boolean isTextViewable(String fileName) { return TEXT.matches(fileName); }
		}
		
		// ── UI 컴포넌트 ──────────────────────────────────────────────
		private Stage      stage;
		private TextField  pathField;
		private Button     browseBtn, scanBtn, stopBtn, saveBtn;
		private Label      statusLabel;
		private ComboBox<String> filterCombo;
		private ListView<String> lineList;
		private ObservableList<String> listItems;
		
		// ── 전체 스캔 결과 (원본) ────────────────────────────────────
		// row[0] = 표시 문자열,  row[1] = 절대파일경로 (파일행만, 나머지 null)
		private final List<String[]> allLines = new ArrayList<>();
		
		// ── 상태 ────────────────────────────────────────────────────
		private ExtFilter       currentFilter = ExtFilter.ALL;
		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		private Thread          scanThread;
		private long            startMs;
		
		// ── 날짜 포맷 ────────────────────────────────────────────────
		private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
		
		// ════════════════════════════════════════════════════════════
		//  show
		// ════════════════════════════════════════════════════════════
		
		private void show(Window owner) {
			stage = new Stage();
			stage.setTitle("📁 SuperDir — 디렉터리 탐색기");
			if (owner != null) stage.initOwner(owner);
			stage.initModality(Modality.NONE);
			
			buildUI();
			
			stage.setWidth(1060);
			stage.setHeight(700);
			if (owner != null) {
				stage.setX(owner.getX() + 40);
				stage.setY(owner.getY() + 40);
				} else {
				stage.centerOnScreen();
			}
			stage.setOnCloseRequest(e -> { stopPrevious(); instance = null; });
			stage.show();
		}
		
		// ════════════════════════════════════════════════════════════
		//  UI 구성
		// ════════════════════════════════════════════════════════════
		
		private void buildUI() {
			
			// ── 경로 입력 행 ──────────────────────────────────────────
			Label pathLbl = makeLabel("경로: ");
			pathField = new TextField();
			pathField.setFont(Font.font("Malgun Gothic", 13));
			pathField.setStyle(
                "-fx-background-color: #F8FCFF;" +
                "-fx-text-fill: " + FG_COLOR + ";" +
                "-fx-border-color: " + BORDER_CLR + ";" +
			"-fx-border-width: 1; -fx-border-radius: 2; -fx-background-radius: 2;");
			pathField.setOnAction(e -> startScan());
			
			browseBtn = makeBtn("📂 폴더 선택");
			
			HBox pathRow = new HBox(6, pathLbl, pathField, browseBtn);
			HBox.setHgrow(pathField, Priority.ALWAYS);
			pathRow.setAlignment(Pos.CENTER_LEFT);
			pathRow.setPadding(new Insets(8, 8, 4, 8));
			pathRow.setStyle("-fx-background-color: transparent;");
			
			// ── 버튼 + 필터 행 ────────────────────────────────────────
			scanBtn = makeBtn("🔍 스캔");
			stopBtn = makeBtn("⏹ 중지");
			saveBtn = makeBtn("💾 저장");
			stopBtn.setDisable(true);
			saveBtn.setDisable(true);
			
			Label filterLbl = makeLabel("  필터: ");
			String[] labels = Arrays.stream(ExtFilter.values())
			.map(f -> f.label).toArray(String[]::new);
			filterCombo = new ComboBox<>(FXCollections.observableArrayList(labels));
			filterCombo.getSelectionModel().select(5); // ALL
			filterCombo.setStyle(
                "-fx-background-color: #F8FCFF; -fx-text-fill: " + FG_COLOR + ";" +
			"-fx-border-color: " + BORDER_CLR + "; -fx-border-width: 1;" +
			"-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;");
			
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			
			Label hintLbl = new Label("  F5: 스캔    ESC: 중지    Ctrl+S: 저장    Enter/더블클릭: 파일 열기  ");
			hintLbl.setStyle("-fx-text-fill: #3C6496; -fx-font-size: 11px; -fx-font-family: 'Malgun Gothic';");
			
			HBox btnRow = new HBox(6, scanBtn, stopBtn, saveBtn, filterLbl, filterCombo, spacer, hintLbl);
			btnRow.setAlignment(Pos.CENTER_LEFT);
			btnRow.setPadding(new Insets(0, 8, 6, 8));
			btnRow.setStyle("-fx-background-color: transparent;");
			
			VBox top = new VBox(pathRow, btnRow);
			top.setStyle(
                "-fx-background-color: " + HEADER_BG + ";" +
                "-fx-border-color: " + BORDER_CLR + ";" +
			"-fx-border-width: 0 0 1 0;");
			
			// ── 결과 목록 ──────────────────────────────────────────────
			listItems = FXCollections.observableArrayList();
			lineList  = new ListView<>(listItems);
			lineList.setFixedCellSize(18);
			lineList.setStyle(
                "-fx-background-color: " + BG_COLOR + ";" +
                "-fx-font-family: 'Courier New';" +
                "-fx-font-size: 13px;" +
			"-fx-border-width: 0;");
			lineList.setCellFactory(lv -> new ListCell<>() {
				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setText(null);
						setStyle("-fx-background-color: " + BG_COLOR + ";");
						return;
					}
					setText(item);
					setFont(Font.font("Courier New", 13));
					setPadding(new Insets(0, 6, 0, 6));
					if (isSelected()) {
						setStyle("-fx-background-color: " + SEL_BG + "; -fx-text-fill: " + FG_COLOR + ";");
						} else if (item.contains("<DIR>")) {
						setStyle("-fx-background-color: " + BG_COLOR + "; -fx-text-fill: " + DIR_COLOR + ";");
						} else if (currentFilter != ExtFilter.ALL && isFileLine(item)) {
						setStyle("-fx-background-color: " + MATCH_BG + "; -fx-text-fill: " + FG_COLOR + ";");
						} else {
						setStyle("-fx-background-color: " + BG_COLOR + "; -fx-text-fill: " + FG_COLOR + ";");
					}
				}
			});
			
			// ── 상태바 ─────────────────────────────────────────────────
			statusLabel = new Label(" 준비");
			statusLabel.setStyle(
                "-fx-text-fill: " + STATUS_FG + ";" +
			"-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;");
			
			HBox statusBar = new HBox(statusLabel);
			statusBar.setPadding(new Insets(3, 8, 3, 8));
			statusBar.setStyle(
                "-fx-background-color: " + STATUS_BG + ";" +
                "-fx-border-color: " + BORDER_CLR + ";" +
			"-fx-border-width: 1 0 0 0;");
			
			// ── 전체 레이아웃 ──────────────────────────────────────────
			BorderPane root = new BorderPane();
			root.setStyle("-fx-background-color: " + BG_COLOR + ";");
			root.setTop(top);
			root.setCenter(lineList);
			root.setBottom(statusBar);
			
			Scene scene = new Scene(root);
			
			// ── 단축키 ────────────────────────────────────────────────
			scene.setOnKeyPressed(e -> {
				switch (e.getCode()) {
					case F5     -> startScan();
					case ESCAPE -> cancelScan();
					case S      -> { if (e.isControlDown()) saveResult(); }
					default     -> {}
				}
			});
			
			// ── 이벤트 ────────────────────────────────────────────────
			browseBtn  .setOnAction(e -> chooseFolder());
			scanBtn    .setOnAction(e -> startScan());
			stopBtn    .setOnAction(e -> cancelScan());
			saveBtn    .setOnAction(e -> saveResult());
			filterCombo.setOnAction(e -> {
				currentFilter = ExtFilter.values()[filterCombo.getSelectionModel().getSelectedIndex()];
				applyFilter();
			});
			
			lineList.setOnKeyPressed  (e -> { if (e.getCode() == KeyCode.ENTER) openSelectedFile(); });
			lineList.setOnMouseClicked(e -> { if (e.getClickCount() == 2)       openSelectedFile(); });
			
			stage.setScene(scene);
		}
		
		// ════════════════════════════════════════════════════════════
		//  폴더 선택
		// ════════════════════════════════════════════════════════════
		
		private void chooseFolder() {
			DirectoryChooser dc = new DirectoryChooser();
			dc.setTitle("스캔할 폴더를 선택하세요");
			String cur = pathField.getText().trim();
			if (!cur.isEmpty()) {
				File f = new File(cur);
				if (f.isDirectory()) dc.setInitialDirectory(f);
			}
			File selected = dc.showDialog(stage);
			if (selected != null) pathField.setText(selected.getAbsolutePath());
		}
		
		// ════════════════════════════════════════════════════════════
		//  스캔
		// ════════════════════════════════════════════════════════════
		
		private void startScan() {
			String path = pathField.getText().trim();
			if (path.isEmpty()) { alert("경로를 입력하세요."); return; }
			File root = new File(path);
			if (!root.isDirectory()) { alert("유효한 폴더가 아닙니다:\n" + path); return; }
			
			stopPrevious();
			allLines.clear();
			listItems.clear();
			cancelled.set(false);
			saveBtn.setDisable(true);
			setScanningState(true);
			
			startMs = System.currentTimeMillis();
			final String startTime = TIME_FMT.format(new Date(startMs));
			
			scanThread = new Thread(() -> {
				
				List<String[]> batch = new ArrayList<>(BATCH_SIZE);
				Deque<File>    stack = new ArrayDeque<>();
				stack.push(root);
				long totalFiles = 0, totalDirs = 0, totalBytes = 0;
				
				while (!stack.isEmpty() && !cancelled.get()) {
					File dir = stack.pop();
					
					addRow(batch, "", null);
					addRow(batch, "  Directory of  " + dir.getAbsolutePath(), null);
					addRow(batch, "", null);
					
					File[] entries = dir.listFiles();
					if (entries == null) {
						addRow(batch, "    [접근 불가 — 권한 없음]", null);
						flushIfFull(batch);
						continue;
					}
					
					Arrays.sort(entries, Comparator
                        .comparing((File f) -> f.isFile() ? 1 : 0)
					.thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
					
					List<File> subDirs  = new ArrayList<>();
					long       dirBytes = 0;
					
					for (File f : entries) {
						if (cancelled.get()) break;
						String date = DATE_FMT.format(new Date(f.lastModified()));
						if (f.isDirectory()) {
							totalDirs++;
							addRow(batch, String.format("  %-19s  %-18s  %s",
							date, "<DIR>", f.getName()), null);
							subDirs.add(f);
							} else {
							totalFiles++;
							long sz = f.length();
							totalBytes += sz;
							dirBytes   += sz;
							addRow(batch, String.format("  %-19s  %,18d  %s",
							date, sz, f.getName()), f.getAbsolutePath());
						}
						flushIfFull(batch);
					}
					
					addRow(batch, String.format(
                        "               합계 파일 %d개  /  %s bytes",
					(long)(entries.length - subDirs.size()), fmt(dirBytes)), null);
					
					for (int i = subDirs.size() - 1; i >= 0; i--)
                    stack.push(subDirs.get(i));
				}
				
				long endMs     = System.currentTimeMillis();
				long elapsedMs = endMs - startMs;
				String endTime = TIME_FMT.format(new Date(endMs));
				
				addRow(batch, "", null);
				addRow(batch, "═══════════════════════════════════════════════════════", null);
				addRow(batch, String.format("  파일  %s 개    합계  %s bytes  (%s KB)",
				fmt(totalFiles), fmt(totalBytes), fmt(totalBytes / 1024)), null);
				addRow(batch, String.format("  폴더  %s 개", fmt(totalDirs)), null);
				addRow(batch, "", null);
				addRow(batch, String.format("  시작: %s   종료: %s   소요: %,d ms",
				startTime, endTime, elapsedMs), null);
				addRow(batch, "═══════════════════════════════════════════════════════", null);
				
				flushBatch(new ArrayList<>(batch), true);
				
			}, "SD-ScanThread");
			scanThread.setDaemon(true);
			scanThread.start();
		}
		
		private void addRow(List<String[]> batch, String display, String filePath) {
			String[] row = {display, filePath};
			allLines.add(row);
			batch.add(row);
		}
		
		// ── 배치 flush ────────────────────────────────────────────────
		
		private void flushIfFull(List<String[]> batch) {
			if (batch.size() >= BATCH_SIZE) {
				flushBatch(new ArrayList<>(batch), false);
				batch.clear();
			}
		}
		
		private void flushBatch(List<String[]> snapshot, boolean done) {
			Platform.runLater(() -> {
				if (done) {
					setScanningState(false);
					saveBtn.setDisable(allLines.isEmpty());
					applyFilter();
					} else {
					statusLabel.setText("🔍 스캔 중...  " + fmt(allLines.size()) + " 건 수집");
				}
			});
		}
		
		// ════════════════════════════════════════════════════════════
		//  중지 / 저장
		// ════════════════════════════════════════════════════════════
		
		private void cancelScan() {
			cancelled.set(true);
			statusLabel.setText("⏹ 중지 요청됨...");
		}
		
		private void stopPrevious() {
			cancelled.set(true);
			if (scanThread != null && scanThread.isAlive()) {
				scanThread.interrupt();
				scanThread = null;
			}
		}
		
		private void saveResult() {
			if (listItems.isEmpty()) return;
			FileChooser fc = new FileChooser();
			fc.setTitle("결과 저장");
			fc.setInitialFileName("superdir_result.txt");
			fc.getExtensionFilters().add(
			new FileChooser.ExtensionFilter("텍스트 파일 (*.txt)", "*.txt"));
			File out = fc.showSaveDialog(stage);
			if (out == null) return;
			final File finalOut = out;
			new Thread(() -> {
				try (PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(finalOut), "UTF-8"))) {
                for (String line : new ArrayList<>(listItems)) pw.println(line);
                Platform.runLater(() -> alert("저장 완료:\n" + finalOut.getAbsolutePath()));
				} catch (Exception ex) {
                Platform.runLater(() -> alert("저장 실패: " + ex.getMessage()));
				}
			}, "SD-SaveThread").start();
		}
		
		// ════════════════════════════════════════════════════════════
		//  텍스트 파일 열기
		// ════════════════════════════════════════════════════════════
		
		private void openSelectedFile() {
			String displayLine = lineList.getSelectionModel().getSelectedItem();
			if (displayLine == null) return;
			
			String filePath = resolveFilePath(displayLine);
			if (filePath == null) { statusLabel.setText(" 파일 행이 아닙니다."); return; }
			
			File file = new File(filePath);
			if (!file.exists()) { statusLabel.setText(" 파일 없음: " + file.getName()); return; }
			if (!ExtFilter.isTextViewable(file.getName())) {
				statusLabel.setText(" 텍스트 뷰어로 열 수 없는 형식: " + file.getName());
				return;
			}
			//openSimpleViewer(file);			
			MainWindow.openTextFileWindow(file);
		}
		
		/**
			* 자체 간이 텍스트 뷰어
			* 인코딩 자동 탐지: UTF-8 → EUC-KR 순서
		*/
		private void openSimpleViewer(File file) {
			new Thread(() -> {
				String content;
				String encLabel;
				try {
					byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
					// UTF-8 시도
					try {
						content  = new String(bytes, "UTF-8");
						if (content.startsWith("\uFEFF")) content = content.substring(1); // BOM 제거
						encLabel = "UTF-8";
						} catch (Exception e1) {
						content  = new String(bytes, "EUC-KR");
						encLabel = "EUC-KR";
					}
					} catch (Exception ex) {
					content  = "[읽기 실패: " + ex.getMessage() + "]";
					encLabel = "?";
				}
				
				final String finalContent = content;
				final String finalEnc     = encLabel;
				
				Platform.runLater(() -> {
					Stage sub = new Stage();
					sub.setTitle("📄 " + file.getName());
					sub.initOwner(stage);
					
					TextArea ta = new TextArea(finalContent);
					ta.setEditable(false);
					ta.setWrapText(false);
					ta.setFont(Font.font("Courier New", 13));
					ta.setStyle("-fx-background-color: " + BG_COLOR + "; -fx-text-fill: " + FG_COLOR + ";");
					
					Label info = new Label(
                        " " + finalEnc + "  |  " + file.getAbsolutePath()
					+ "  (" + fmt(file.length()) + " bytes)");
					info.setStyle(
                        "-fx-text-fill: " + STATUS_FG + ";" +
                        "-fx-background-color: " + STATUS_BG + ";" +
                        "-fx-font-size: 11px; -fx-font-family: 'Malgun Gothic';" +
					"-fx-border-color: " + BORDER_CLR + "; -fx-border-width: 1 0 0 0;");
					info.setPadding(new Insets(2, 4, 2, 4));
					info.setMaxWidth(Double.MAX_VALUE);
					
					BorderPane bp = new BorderPane(ta);
					bp.setBottom(info);
					bp.setStyle("-fx-background-color: " + BG_COLOR + ";");
					
					sub.setScene(new Scene(bp, 900, 650));
					sub.show();
					statusLabel.setText(" 열기: " + file.getName() + "  " + finalEnc);
				});
			}, "SD-ViewThread").start();
		}
		
		// ════════════════════════════════════════════════════════════
		//  필터 적용
		// ════════════════════════════════════════════════════════════
		
		/**
			* allLines 를 현재 필터로 재구성하여 listItems 갱신.
			*  - ALL: 전부 표시
			*  - 기타 필터: "Directory of" 블록 단위로 분리 후
			*    매칭 파일이 있는 블록만 표시
		*/
		private void applyFilter() {
			if (allLines.isEmpty()) return;
			listItems.clear();
			
			if (currentFilter == ExtFilter.ALL) {
				for (String[] row : allLines) listItems.add(row[0]);
				updateFilterStatus();
				return;
			}
			
			List<List<String[]>> blocks = new ArrayList<>();
			List<String[]> cur = null;
			
			for (String[] row : allLines) {
				if (row[0].contains("Directory of")) {
					if (cur != null) blocks.add(cur);
					cur = new ArrayList<>();
				}
				if (cur == null) cur = new ArrayList<>();
				cur.add(row);
			}
			if (cur != null && !cur.isEmpty()) blocks.add(cur);
			
			for (List<String[]> block : blocks) flushFilterBlock(block);
			updateFilterStatus();
		}
		
		private void flushFilterBlock(List<String[]> block) {
			long matched = block.stream()
			.filter(r -> r[1] != null && currentFilter.matches(new File(r[1]).getName()))
			.count();
			if (matched == 0) return;
			for (String[] row : block) {
				if (row[1] != null) {
					if (currentFilter.matches(new File(row[1]).getName()))
                    listItems.add(row[0]);
					} else {
					listItems.add(row[0]);
				}
			}
		}
		
		private void updateFilterStatus() {
			statusLabel.setText("🔍 필터: [" + currentFilter.label + "]  —  "
			+ fmt(listItems.size()) + " 줄");
		}
		
		// ════════════════════════════════════════════════════════════
		//  유틸
		// ════════════════════════════════════════════════════════════
		
		/** 표시 문자열 → 절대파일경로 역추적 */
		private String resolveFilePath(String displayLine) {
			if (displayLine == null || displayLine.isBlank()) return null;
			for (String[] row : allLines) {
				if (row[1] != null && row[0].equals(displayLine)) return row[1];
			}
			return null;
		}
		
		/** 파일 행 여부 판단 (날짜 패턴: "  2024-01-01 ...") */
		private boolean isFileLine(String line) {
			if (line == null || line.length() < 22) return false;
			String t = line.stripLeading();
			return t.length() > 19
			&& Character.isDigit(t.charAt(0))
			&& t.charAt(4) == '-'
			&& !t.contains("<DIR>");
		}
		
		private void setScanningState(boolean scanning) {
			scanBtn    .setDisable( scanning);
			stopBtn    .setDisable(!scanning);
			browseBtn  .setDisable( scanning);
			pathField  .setDisable( scanning);
			filterCombo.setDisable( scanning);
			statusLabel.setText(scanning ? "🔍 스캔 중..." : " 준비");
		}
		
		private void alert(String msg) {
			Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
			a.setHeaderText(null);
			a.setTitle("SuperDir");
			a.initOwner(stage);
			a.showAndWait();
		}
		
		// ── 팩토리 헬퍼 ──────────────────────────────────────────────
		
		private static Label makeLabel(String text) {
			Label l = new Label(text);
			l.setFont(Font.font("Malgun Gothic", 13));
			l.setStyle("-fx-text-fill: " + FG_COLOR + ";");
			return l;
		}
		
		private static Button makeBtn(String text) {
			Button b = new Button(text);
			b.setFont(Font.font("Malgun Gothic", 13));
			b.setStyle(
                "-fx-background-color: " + BTN_BG + ";" +
                "-fx-text-fill: " + FG_COLOR + ";" +
                "-fx-border-color: " + BORDER_CLR + ";" +
                "-fx-border-width: 1; -fx-border-radius: 2; -fx-background-radius: 2;" +
			"-fx-cursor: hand; -fx-padding: 3 10 3 10;");
			// hover 효과
			b.setOnMouseEntered(e -> b.setStyle(b.getStyle().replace(BTN_BG, "#B8DBF5")));
			b.setOnMouseExited (e -> b.setStyle(b.getStyle().replace("#B8DBF5", BTN_BG)));
			return b;
		}
		private static String fmt(long n) { return String.format("%,d", n); }
	}
	//  SuperDir
	
	
	
	
    // 인스턴스화 불가
    private BackgroundPlayer() {}
	
    // ══════════════════════════════════════════════════════════════
    //  YoutubePlayer
    // ══════════════════════════════════════════════════════════════
	
    /**
		* YouTube / 라이브 스트림 배경 재생기.
		*
		* 동작 원리:
		*  1) yt-dlp 로 스트림 URL 추출
		*  2) ffmpeg 로 30초 청크 다운로드 → 로컬 MP4
		*  3) JavaFX MediaPlayer 로 재생
		*  4) 재생 중 다음 청크 미리 다운로드
		*  5) 전환 시 끊김 ~0.5~1초 (배경용 허용 범위)
		*
		* yt-dlp.exe / ffmpeg.exe 는 앱 폴더(또는 tools/ 서브폴더) 또는 PATH 에 있어야 함.
	*/
    public static class YoutubePlayer {
		
        // ── 콜백 인터페이스 ────────────────────────────────────────
        public interface HostCallback {
            /** FX 스레드. MediaView를 씬 그래프 숨김 그룹에 추가 (스냅샷용). */
            void attachMediaView(javafx.scene.Node view);
            /** FX 스레드. MediaView를 씬 그래프에서 제거. */
            void detachMediaView();
            /** FX 스레드. 스냅샷 프레임을 시계 코인 페이스에 주입. */
            void onYoutubeFrame(javafx.scene.image.WritableImage frame);
            /** FX 스레드. YouTube 종료 시 코인 페이스 초기화. */
            void clearYoutubeFrame();
            /** FX 스레드. 상태 메시지 표시 (빈 문자열이면 메시지 숨김). */
            void onStatusMessage(String message);
            /** %APPDATA%\KootPanKingThree\settings\ */
            String getSettingsDir();
            /** ini에 저장된 yt-dlp.exe 절대 경로. */
            String getYtDlpPath();
            /** ini에 저장된 ffmpeg.exe 절대 경로. */
            String getFfmpegPath();
		}
		
        // ── 상수 ───────────────────────────────────────────────────
        private static final int  CHUNK_SECONDS = 30;
        private static final long URL_EXPIRE_MS = 6L * 3600 * 1000; // 6시간
		
        // ── 상태 ───────────────────────────────────────────────────
        private final HostCallback           host;
        private volatile boolean             running = false;
        private Thread                       workerThread;
        private MediaPlayer                  currentPlayer;
        private MediaPlayer                  audioPlayer;       // 로컬 MP4 오디오 전담
        private double                       currentVolume = 1.0; // 0.0~1.0
        private javafx.animation.AnimationTimer captureTimer;  // 프레임 캡처 타이머
        private Path                         cacheDir;
		
        // ── 생성자 ─────────────────────────────────────────────────
        public YoutubePlayer(HostCallback host) {
            this.host = host;
		}
		
        // ── 공개 API ───────────────────────────────────────────────
		
        /** YouTube / 라이브 스트림 배경 시작. FX 스레드에서 호출. */
        public void start(String youtubeUrl) {
            stop();
            running  = true;
            cacheDir = Path.of(host.getSettingsDir()).getParent().resolve("cache");
            try { Files.createDirectories(cacheDir); } catch (Exception ignored) {}
			
            workerThread = new Thread(() -> workerLoop(youtubeUrl), "YT-BG-Worker");
            workerThread.setDaemon(true);
            workerThread.start();
            System.out.println("[YT-BG] 시작: " + youtubeUrl);
		}
		
        /** 배경 재생 중지. FX 스레드에서 호출. */
        public void stop() {
            running = false;
            if (workerThread != null) {
                workerThread.interrupt();
                workerThread = null;
			}
            Platform.runLater(() -> {
                if (captureTimer != null) { captureTimer.stop(); captureTimer = null; }
                if (currentPlayer != null) {
                    currentPlayer.stop();
                    currentPlayer.dispose();
                    currentPlayer = null;
				}
                if (audioPlayer != null) {
                    audioPlayer.stop();
                    audioPlayer.dispose();
                    audioPlayer = null;
				}
                host.detachMediaView();
                host.clearYoutubeFrame();
			});
            cleanCache();
            System.out.println("[YT-BG] 중지");
		}
		
        /**
			* 로컬 MP4 볼륨 조절 (0.0 ~ 1.0).
			* 재생 중에도 즉시 반영된다.
		*/
        public void setVolume(double volume) {
            currentVolume = Math.max(0.0, Math.min(1.0, volume));
            Platform.runLater(() -> {
                if (audioPlayer != null) audioPlayer.setVolume(currentVolume);
			});
		}
		
        public double getVolume() { return currentVolume; }
		
        public boolean isRunning() { return running; }
		
        // ── INI 유틸리티 (static) ──────────────────────────────────
		
        /**
			* youTubeCctv.ini 파싱.
			* 형식: [도시명]https://...
			* @return List of { cityName, url }
		*/
        public static List<String[]> loadStreamIni(String settingsDir) {
            List<String[]> list = new ArrayList<>();
            File iniFile = new File(settingsDir, "youTubeCctv.ini");
            if (!iniFile.exists()) {
                System.out.println("[YT-INI] 파일 없음: " + iniFile.getAbsolutePath());
                return list;
			}
            try (BufferedReader br = new BufferedReader(
			new InputStreamReader(new FileInputStream(iniFile), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("[")) {
					int end = line.indexOf(']');
					if (end > 0 && end < line.length() - 1) {
						String name = line.substring(1, end).trim();
						String url  = line.substring(end + 1).trim();
						if (!name.isEmpty() && !url.isEmpty())
						list.add(new String[]{name, url});
					}
				}
			}
            } catch (Exception e) {
			System.out.println("[YT-INI] 읽기 오류: " + e.getMessage());
            }
            System.out.println("[YT-INI] " + list.size() + "개 로드");
            return list;
		}
		
        /** GitHub 에서 youTubeCctv.ini 다운로드 */
        public static void downloadIni(String settingsDir) {
            String url = "https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/INI_bak/youTubeCctv.ini";
            File dest = new File(settingsDir, "youTubeCctv.ini");
            try {
                //  java.net.URL src = new java.net.URL(url);
				java.net.URL src;
				try {
					src = new java.net.URI(url.trim()).toURL();
				} catch (Exception e) {	throw new RuntimeException(e);}
				
				try (InputStream in = src.openStream();
					FileOutputStream out = new FileOutputStream(dest)) {
                    in.transferTo(out);
				}
                System.out.println("[YT-INI] 다운로드 완료: " + dest.getAbsolutePath());
				} catch (Exception e) {
                System.out.println("[YT-INI] 다운로드 실패: " + e.getMessage());
			}
		}
		
        // ── 워커 루프 (백그라운드 스레드) ─────────────────────────
		
        private void workerLoop(String youtubeUrl) {
            int    chunkIdx    = 0;
            long   urlFetchedAt = 0;
            String streamUrl   = "";
            boolean firstChunk = true; // 첫 번째 청크 여부 (로딩 메시지 표시)
			
            Path[] chunks = {
                cacheDir.resolve("yt_chunk_0.mp4"),
                cacheDir.resolve("yt_chunk_1.mp4")
			};
			
            while (running) {
                try {
                    // ① URL 만료 or 최초 → yt-dlp 재추출
                    if (streamUrl.isEmpty() ||
						System.currentTimeMillis() - urlFetchedAt > URL_EXPIRE_MS) {
                        if (firstChunk)
						Platform.runLater(() -> host.onStatusMessage("📡 스트림 주소 확인 중..."));
                        System.out.println("[YT-BG] yt-dlp URL 추출 중...");
                        streamUrl = extractStreamUrl(youtubeUrl);
                        if (streamUrl.isEmpty()) {
                            System.out.println("[YT-BG] URL 추출 실패 → 3초 후 재시도");
                            sleep(3000); continue;
						}
                        urlFetchedAt = System.currentTimeMillis();
                        System.out.println("[YT-BG] 스트림 URL 추출 성공");
					}
					
                    // ② 현재 청크 다운로드
                    Path currentChunk = chunks[chunkIdx % 2];
                    if (firstChunk)
					Platform.runLater(() -> host.onStatusMessage(
					"⬇ 영상 다운로드 중입니다. 잠시만 기다리세요... (최대 30초)"));
                    System.out.println("[YT-BG] 청크 다운로드: chunk_" + (chunkIdx % 2) + ".mp4");
                    if (!downloadChunk(streamUrl, currentChunk) || !running) {
                        streamUrl = ""; // URL 만료 가능성 → 재추출
                        sleep(2000); continue;
					}
					
                    // ③ 현재 청크 재생 + 다음 청크 미리 다운로드
                    if (firstChunk) {
                        Platform.runLater(() -> host.onStatusMessage("")); // 메시지 숨김
                        firstChunk = false;
					}
                    CountDownLatch playDone   = new CountDownLatch(1);
                    Path           playPath   = currentChunk;
                    Platform.runLater(() -> playChunk(playPath, playDone));
					
                    Path nextChunk = chunks[(chunkIdx + 1) % 2];
                    if (running) {
                        System.out.println("[YT-BG] 다음 청크 미리 다운로드 중...");
                        downloadChunk(streamUrl, nextChunk);
					}
					
                    // ④ 재생 완료 대기
                    playDone.await(CHUNK_SECONDS + 15, TimeUnit.SECONDS);
                    chunkIdx++;
					
					} catch (InterruptedException e) {
                    break;
					} catch (Exception e) {
                    System.out.println("[YT-BG] 오류: " + e.getMessage());
                    sleep(3000);
				}
			}
            System.out.println("[YT-BG] 워커 종료");
		}
		
        // ── yt-dlp: 스트림 URL 추출 ────────────────────────────────
        private String extractStreamUrl(String youtubeUrl) {
            try {
                String ytdlp = host.getYtDlpPath();
                if (ytdlp == null || ytdlp.isEmpty()) {
                    System.out.println("[YT-BG] yt-dlp 경로 없음 → 중단");
                    return "";
				}
                ProcessBuilder pb = new ProcessBuilder(
                    ytdlp,
                    "-f", "bestvideo[ext=mp4][height<=720]/bestvideo[ext=mp4]/best[ext=mp4]",
                    "--get-url", "--no-playlist", youtubeUrl
				);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String url = new BufferedReader(
				new InputStreamReader(proc.getInputStream(), "UTF-8"))
				.lines().filter(l -> l.startsWith("http"))
				.findFirst().orElse("");
                proc.waitFor();
                return url.trim();
				} catch (Exception e) {
                System.out.println("[YT-BG] yt-dlp 오류: " + e.getMessage());
                return "";
			}
		}
		
        // ── ffmpeg: 30초 청크 다운로드 ────────────────────────────
        private boolean downloadChunk(String streamUrl, Path output) {
            try {
                String ffmpeg = host.getFfmpegPath();
                if (ffmpeg == null || ffmpeg.isEmpty()) {
                    System.out.println("[YT-BG] ffmpeg 경로 없음 → 중단");
                    return false;
				}
                Files.deleteIfExists(output);
                ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y",
                    "-reconnect",           "1",
                    "-reconnect_streamed",  "1",
                    "-reconnect_delay_max", "5",
                    "-i",                  streamUrl,
                    "-t",                  String.valueOf(CHUNK_SECONDS),
                    "-c",                  "copy",
                    "-movflags",           "+faststart",
                    output.toString()
				);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                new Thread(() -> {
                    try { proc.getInputStream().transferTo(OutputStream.nullOutputStream()); }
                    catch (Exception ignored) {}
				}, "YT-ffmpeg-drain").start();
				
                int     exit = proc.waitFor();
                boolean ok   = exit == 0 && Files.exists(output) && Files.size(output) > 1024;
                System.out.println("[YT-BG] 청크 " + (ok ? "성공" : "실패")
				+ " (" + (Files.exists(output) ? Files.size(output) / 1024 + "KB" : "없음") + ")");
                return ok;
				} catch (Exception e) {
                System.out.println("[YT-BG] ffmpeg 오류: " + e.getMessage());
                return false;
			}
		}
		
        // ── MediaPlayer: 청크 재생 + AnimationTimer 프레임 캡처 (FX 스레드) ──
        private void playChunk(Path chunkFile, CountDownLatch latch) {
            if (!running) { latch.countDown(); return; }
            try {
                // ① 이전 타이머 / 플레이어 정리
                if (captureTimer != null) { captureTimer.stop(); captureTimer = null; }
                if (currentPlayer != null) {
                    currentPlayer.stop();
                    currentPlayer.dispose();
                    currentPlayer = null;
				}
				
                // ② MediaPlayer + MediaView 생성
                Media     media = new Media(chunkFile.toUri().toString());
                currentPlayer   = new MediaPlayer(media);
                currentPlayer.setMute(true);
                currentPlayer.setAutoPlay(false);
				
                MediaView view = new MediaView(currentPlayer);
                view.setFitWidth(640);
                view.setFitHeight(360);
                view.setPreserveRatio(false);
				
                // ③ 씬 그래프 숨김 그룹에 추가 (스냅샷 렌더링용)
                host.attachMediaView(view);
				
                // ④ AnimationTimer: ~30fps 스냅샷 → 코인 페이스 주입
                captureTimer = new javafx.animation.AnimationTimer() {
                    private long lastNs = 0;
                    private final javafx.scene.SnapshotParameters params =
					new javafx.scene.SnapshotParameters();
                    @Override public void handle(long now) {
                        if (now - lastNs < 33_000_000L) return; // 30fps 제한
                        lastNs = now;
                        javafx.scene.image.WritableImage wi = view.snapshot(params, null);
                        if (wi != null) host.onYoutubeFrame(wi);
					}
				};
				
                currentPlayer.setOnReady(() -> {
                    captureTimer.start();
                    currentPlayer.play();
                    System.out.println("[YT-BG] 재생 시작: " + chunkFile.getFileName());
				});
                currentPlayer.setOnEndOfMedia(() -> {
                    captureTimer.stop();
                    latch.countDown();
				});
                currentPlayer.setOnError(() -> {
                    System.out.println("[YT-BG] 재생 오류: " + currentPlayer.getError());
                    if (captureTimer != null) captureTimer.stop();
                    latch.countDown();
				});
				
				} catch (Exception e) {
                System.out.println("[YT-BG] 재생 준비 오류: " + e.getMessage());
                latch.countDown();
			}
		}
		
        // ══════════════════════════════════════════════════════════════
        //  로컬 MP4 재생 (FFmpeg 파이프 → 모든 코덱 지원)
        //  ffmpeg 없으면 JavaFX MediaPlayer 로 폴백 (H.264 한정)
        // ══════════════════════════════════════════════════════════════
		
        /**
			* 로컬 MP4 파일 배경 재생.
			*  - ffmpeg 있음 : rawvideo 파이프 → WritableImage → 코인 페이스 주입 (모든 코덱)
			*  - ffmpeg 없음 : JavaFX MediaPlayer 폴백 (H.264 + AAC 한정)
			* FX 스레드에서 호출.
		*/
        public void startLocalMp4(File mp4File) {
            stop();
            running = true;
			
            String ffmpeg = host.getFfmpegPath();
            boolean hasFfmpeg = ffmpeg != null && !ffmpeg.isEmpty()
			&& new File(ffmpeg).exists();
			
            if (hasFfmpeg) {
                startLocalMp4Ffmpeg(mp4File, ffmpeg);
				} else {
                startLocalMp4Javafx(mp4File);
                Platform.runLater(() ->
				host.onStatusMessage("⚠ ffmpeg 미설정 → H.264 전용 모드"));
			}
			
            // 오디오는 항상 JavaFX MediaPlayer 가 담당 (ffmpeg 파이프는 -an 으로 영상만 처리)
            // JavaFX 폴백 모드일 때는 startLocalMp4Javafx 내부에서 이미 재생하므로 중복 방지
            if (hasFfmpeg) {
                startLocalMp4Audio(mp4File);
			}
			
            System.out.println("[LocalMP4] 시작: " + mp4File.getName()
			+ (hasFfmpeg ? " (FFmpeg 파이프 + JavaFX 오디오)" : " (JavaFX 폴백)"));
		}
		
        /** 오디오 전담 MediaPlayer 시작 (FFmpeg 파이프 방식일 때만 호출). FX 스레드 필요. */
        private void startLocalMp4Audio(File mp4File) {
            Platform.runLater(() -> {
                try {
                    if (audioPlayer != null) {
                        audioPlayer.stop();
                        audioPlayer.dispose();
                        audioPlayer = null;
					}
                    Media media = new Media(mp4File.toURI().toString());
                    audioPlayer = new MediaPlayer(media);
                    audioPlayer.setMute(false);
                    audioPlayer.setVolume(currentVolume);
                    audioPlayer.setCycleCount(MediaPlayer.INDEFINITE); // 무한 반복
                    audioPlayer.setOnError(() ->
					System.out.println("[LocalMP4-Audio] 재생 오류: " + audioPlayer.getError()));
                    audioPlayer.play();
                    System.out.println("[LocalMP4-Audio] 오디오 재생 시작: " + mp4File.getName());
					} catch (Exception e) {
                    System.out.println("[LocalMP4-Audio] 시작 실패: " + e.getMessage());
				}
			});
		}
		
        // ── FFmpeg 파이프 방식 ─────────────────────────────────────
        private void startLocalMp4Ffmpeg(File mp4File, String ffmpegExe) {
            // 실제 해상도 probe: 세로 영상이면 360×640, 가로면 640×360
            int[] dim = probeVideoDimensions(mp4File, ffmpegExe);
            final int W = dim[0], H = dim[1];
            final int frameBytes = W * H * 3; // BGR24
            System.out.println("[LocalMP4-FF] 출력 해상도: " + W + "×" + H
			+ (H > W ? " (세로 영상)" : " (가로 영상)"));
			
            workerThread = new Thread(() -> {
                Process proc = null;
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        ffmpegExe,
                        "-re",                          // 실시간 속도 (배경 과부하 방지)
                        "-stream_loop", "-1",           // 무한 반복
                        "-i", mp4File.getAbsolutePath(),
                        "-vf", "scale=" + W + ":" + H,
                        "-f",  "rawvideo",
                        "-pix_fmt", "bgr24",            // TYPE_3BYTE_BGR 과 1:1 대응
                        "-an",                          // 오디오 제거 (배경 무음)
                        "pipe:1"
					);
                    pb.redirectErrorStream(false);
                    proc = pb.start();
					
                    // stderr 드레인 (블로킹 방지)
                    final Process finalProc = proc;
                    new Thread(() -> {
                        try { finalProc.getErrorStream()
						.transferTo(OutputStream.nullOutputStream()); }
                        catch (Exception ignored) {}
					}, "LocalMP4-stderr").start();
					
                    InputStream stdout = proc.getInputStream();
                    byte[] buf = new byte[frameBytes];
					
                    Platform.runLater(() -> host.onStatusMessage(""));
					
                    while (running) {
                        // 정확히 한 프레임(frameBytes) 읽기
                        int read = 0;
                        while (read < frameBytes) {
                            int n = stdout.read(buf, read, frameBytes - read);
                            if (n < 0) { running = false; break; } // EOF
                            read += n;
						}
                        if (!running) break;
						
                        // BGR → BufferedImage
                        java.awt.image.BufferedImage img =
						new java.awt.image.BufferedImage(W, H,
						java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
                        img.getRaster().setDataElements(0, 0, W, H, buf.clone());
						
                        // BufferedImage → WritableImage
                        javafx.scene.image.WritableImage wImg =
						javafx.embed.swing.SwingFXUtils.toFXImage(img, null);
						
                        Platform.runLater(() -> host.onYoutubeFrame(wImg));
					}
					
					} catch (Exception e) {
                    System.out.println("[LocalMP4-FF] 오류: " + e.getMessage());
					} finally {
                    if (proc != null) proc.destroyForcibly();
                    Platform.runLater(() -> {
                        host.clearYoutubeFrame();
                        host.detachMediaView();
					});
                    System.out.println("[LocalMP4-FF] 워커 종료");
				}
			}, "LocalMP4-FF-Worker");
            workerThread.setDaemon(true);
            workerThread.start();
		}
		
        // ── JavaFX MediaPlayer 폴백 (H.264 한정) ──────────────────
        private void startLocalMp4Javafx(File mp4File) {
            Platform.runLater(() -> {
                try {
                    if (captureTimer  != null) { captureTimer.stop();  captureTimer  = null; }
                    if (currentPlayer != null) {
                        currentPlayer.stop(); currentPlayer.dispose(); currentPlayer = null;
					}
					
                    Media      media  = new Media(mp4File.toURI().toString());
                    currentPlayer     = new MediaPlayer(media);
                    currentPlayer.setCycleCount(MediaPlayer.INDEFINITE); // 무한 반복
                    currentPlayer.setMute(false);  // 폴백 모드: JavaFX 자체 오디오 사용
                    currentPlayer.setVolume(currentVolume);
                    currentPlayer.setAutoPlay(false);
					
                    MediaView view = new MediaView(currentPlayer);
                    // 초기값: 가로 기준. onReady 에서 실제 해상도로 재조정
                    view.setFitWidth(640);
                    view.setFitHeight(640);   // 세로 영상 대응용 충분한 높이
                    view.setPreserveRatio(true);
                    host.attachMediaView(view);
					
                    // AnimationTimer: ~30fps 스냅샷 → 코인 페이스 주입
                    captureTimer = new javafx.animation.AnimationTimer() {
                        private long lastNs = 0;
                        private final javafx.scene.SnapshotParameters params =
						new javafx.scene.SnapshotParameters();
                        @Override public void handle(long now) {
                            if (now - lastNs < 33_000_000L) return;
                            lastNs = now;
                            javafx.scene.image.WritableImage wi =
							view.snapshot(params, null);
                            if (wi != null) host.onYoutubeFrame(wi);
						}
					};
					
                    currentPlayer.setOnReady(() -> {
                        // 실제 영상 해상도로 fitWidth/Height 결정
                        int vw = currentPlayer.getMedia().getWidth();
                        int vh = currentPlayer.getMedia().getHeight();
                        if (vw > 0 && vh > 0) {
                            if (vh > vw) {
                                // 세로 영상: 높이 640 기준
                                view.setFitWidth(640);
                                view.setFitHeight(640);
								} else {
                                // 가로 영상: 너비 640 기준
                                view.setFitWidth(640);
                                view.setFitHeight(360);
							}
                            System.out.println("[LocalMP4-FX] 해상도=" + vw + "×" + vh
							+ (vh > vw ? " (세로)" : " (가로)"));
						}
                        captureTimer.start();
                        currentPlayer.play();
                        System.out.println("[LocalMP4-FX] 재생 시작: "
						+ mp4File.getName());
					});
                    currentPlayer.setOnError(() -> {
                        System.out.println("[LocalMP4-FX] 재생 오류: "
						+ currentPlayer.getError());
                        if (captureTimer != null) { captureTimer.stop(); captureTimer = null; }
                        Platform.runLater(() -> {
                            host.clearYoutubeFrame();
                            host.detachMediaView();
						});
					});
					
					} catch (Exception e) {
                    System.out.println("[LocalMP4-FX] 시작 실패: " + e.getMessage());
				}
			});
		}
		
        // ── 영상 해상도 probe (ffmpeg -i) ─────────────────────────
        /**
			* ffmpeg -i 로 영상의 실제 해상도를 읽어 출력 해상도를 결정한다.
			* · 가로 영상 (w >= h) → 640×360
			* · 세로 영상 (h >  w) → 360×640
			* probe 실패 시 기본값 640×360 반환.
		*/
        private int[] probeVideoDimensions(File mp4File, String ffmpegExe) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    ffmpegExe, "-i", mp4File.getAbsolutePath()
				);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
				
                String output;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
				new java.io.InputStreamReader(proc.getInputStream(), "UTF-8"))) {
				output = br.lines().collect(java.util.stream.Collectors.joining("\n"));
                }
                proc.waitFor();
				
                // "Stream #0:0: Video: ... 1280x720" 또는 "1080x1920" 형태 파싱
                java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("Video:.*?(\\d{2,5})x(\\d{2,5})")
				.matcher(output);
                if (m.find()) {
                    int srcW = Integer.parseInt(m.group(1));
                    int srcH = Integer.parseInt(m.group(2));
                    System.out.println("[LocalMP4-FF] 원본 해상도: " + srcW + "×" + srcH);
                    if (srcH > srcW) {
                        return new int[]{360, 640};  // 세로 영상
						} else {
                        return new int[]{640, 360};  // 가로 영상
					}
				}
				} catch (Exception e) {
                System.out.println("[LocalMP4-FF] probe 실패: " + e.getMessage());
			}
            return new int[]{640, 360}; // 기본값
		}
		
        // ── 유틸 ───────────────────────────────────────────────────
        private void cleanCache() {
            if (cacheDir == null) return;
            try {
                Files.deleteIfExists(cacheDir.resolve("yt_chunk_0.mp4"));
                Files.deleteIfExists(cacheDir.resolve("yt_chunk_1.mp4"));
			} catch (Exception ignored) {}
		}
		
        private void sleep(long ms) {
            try { Thread.sleep(ms); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		}
	}
	
    // ══════════════════════════════════════════════════════════════
    //  CalendarAlarmPoller
    // ══════════════════════════════════════════════════════════════
	
    /**
		* Google + 네이버 캘린더 이벤트 폴링 및 알림 트리거.
		*
		* 기능:
		*   ① 1분 주기로 Google Calendar + 네이버 Calendar 이벤트 조회
		*   ② 이벤트 시작 시각 도달 시 텔레그램 메시지 전송
		*   ③ 매일 아침 자동 브리핑 텔레그램 전송 (지정 시각)
	*/
    public static class CalendarAlarmPoller {
		
        // ── 알림 설정 ─────────────────────────────────────────────
        private static final int ALARM_WINDOW_MINUTES       = 1;
        private static final int COOLDOWN_MINUTES           = 5;
        private static final int MORNING_BRIEF_TIME_DEFAULT = 700;
		
        // ── 의존성 ────────────────────────────────────────────────
        private final GoogleCalendarService googleCalendar;
        private final NaverCalendarService  naverCalendar;
        private final TelegramBot           telegramBot;
        private final String                telegramChatId;
        private final int                   morningBriefTime;
		
        // ── 내부 상태 ─────────────────────────────────────────────
        private ScheduledExecutorService scheduler   = null;
        private ScheduledFuture<?>       pollFuture  = null;
        private volatile boolean         running     = false;
        private final Map<String, Long>  firedAlarms = new HashMap<>();
        private int lastBriefDay = -1;
		
        // ── ini 변경 감지 ─────────────────────────────────────────
        private String                   iniFilePath     = "";
        private long                     iniLastModified = 0L;
        private ScheduledExecutorService iniWatcher      = null;
		
        // ── 생성자 ────────────────────────────────────────────────
        public CalendarAlarmPoller(GoogleCalendarService googleCalendar,
			NaverCalendarService  naverCalendar,
			TelegramBot           telegramBot,
			String                telegramChatId,
			int                   morningBriefTime) {
            this.googleCalendar   = googleCalendar;
            this.naverCalendar    = naverCalendar;
            this.telegramBot      = telegramBot;
            this.telegramChatId   = (telegramChatId != null) ? telegramChatId : "";
            this.morningBriefTime = morningBriefTime;
		}
		
        public CalendarAlarmPoller(GoogleCalendarService googleCalendar,
			NaverCalendarService  naverCalendar,
			TelegramBot           telegramBot,
			String                telegramChatId) {
            this(googleCalendar, naverCalendar, telegramBot, telegramChatId,
			MORNING_BRIEF_TIME_DEFAULT);
		}
		
        // ── ini 감시 ──────────────────────────────────────────────
        public void setIniFile(String iniFilePath) {
            this.iniFilePath     = (iniFilePath != null) ? iniFilePath : "";
            File f               = new File(this.iniFilePath);
            this.iniLastModified = f.exists() ? f.lastModified() : 0L;
            startIniWatcher();
		}
		
        private void startIniWatcher() {
            if (iniWatcher != null) return;
            iniWatcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CalIniWatcher");
                t.setDaemon(true);
                return t;
			});
            iniWatcher.scheduleAtFixedRate(() -> {
                try { checkIniChange(); }
                catch (Exception e) {
                    System.out.println("[CalPoller] ini 감시 오류: " + e.getMessage());
				}
			}, 10, 30, TimeUnit.SECONDS);
            System.out.println("[CalPoller] ini 감시 시작 (30초 간격): " + iniFilePath);
		}
		
        // ── 시작 / 중지 ───────────────────────────────────────────
        public void start() {
            if (running) return;
			
            boolean googleOk = googleCalendar != null && googleCalendar.isInitialized();
            boolean naverOk  = naverCalendar  != null && naverCalendar.isInitialized();
			
            if (!googleOk && !naverOk) {
                System.out.println("[CalPoller] 초기화된 캘린더 서비스 없음 - 시작 불가");
                return;
			}
			
            running   = true;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CalendarPoller");
                t.setDaemon(true);
                return t;
			});
			
            pollFuture = scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkAlarms();
                    checkMorningBrief();
					} catch (Exception e) {
                    System.out.println("[CalPoller] 폴링 오류: " + e.getMessage());
				}
			}, 3, 60, TimeUnit.SECONDS);
			
            String sources = (googleOk ? "구글" : "")
			+ (googleOk && naverOk ? " + " : "")
			+ (naverOk ? "네이버" : "");
            System.out.println("[CalPoller] 폴링 시작 (1분 간격) - " + sources);
		}
		
        public void stop() {
            running = false;
            if (pollFuture != null) { pollFuture.cancel(false); pollFuture = null; }
            if (scheduler  != null) { scheduler.shutdown();     scheduler  = null; }
            System.out.println("[CalPoller] 폴링 중지 (ini 감시는 유지)");
		}
		
        /** 완전 종료 — 앱 종료 시 호출 (ini 감시까지 모두 중지) */
        public void stopAll() {
            stop();
            if (iniWatcher != null) { iniWatcher.shutdown(); iniWatcher = null; }
            System.out.println("[CalPoller] 완전 종료");
		}
		
        public boolean isRunning() { return running; }
		
        // ── ini 변경 감지 ─────────────────────────────────────────
        private void checkIniChange() {
            if (iniFilePath.isEmpty()) return;
            File f = new File(iniFilePath);
            if (!f.exists()) return;
			
            long modified = f.lastModified();
            if (modified <= iniLastModified) return;
            iniLastModified = modified;
            System.out.println("[CalPoller] ini 변경 감지 - 캘린더 정책 재확인");
			
            try (FileInputStream fis = new FileInputStream(f)) {
                Properties props = new Properties();
                props.load(fis);
				
                boolean googleEnabled = Boolean.parseBoolean(
				props.getProperty("google.calendar.enabled", "true"));
                boolean naverEnabled  = Boolean.parseBoolean(
				props.getProperty("naver.calendar.enabled",  "true"));
				
                boolean googleActive  = googleCalendar != null && googleCalendar.isInitialized();
                boolean naverActive   = naverCalendar  != null && naverCalendar.isInitialized();
				
                if (running) {
                    if (!googleEnabled && googleActive) {
                        System.out.println("[CalPoller] ini 정책: Google 비활성화 → 중지");
                        stop(); return;
					}
                    if (!naverEnabled && naverActive) {
                        System.out.println("[CalPoller] ini 정책: 네이버 비활성화 → 중지");
                        stop(); return;
					}
				}
                if (!running) {
                    boolean canGoogle = googleEnabled && googleActive;
                    boolean canNaver  = naverEnabled  && naverActive;
                    if (canGoogle || canNaver) {
                        System.out.println("[CalPoller] ini 정책: 재활성화 → 폴러 재기동");
                        start();
					}
				}
				} catch (Exception e) {
                System.out.println("[CalPoller] ini 재확인 오류: " + e.getMessage());
			}
		}
		
        // ── 알람 체크 ─────────────────────────────────────────────
        private void checkAlarms() {
            ZonedDateTime now = ZonedDateTime.now();
            System.out.println("[CalPoller] 알람 체크 "
                + now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " | Google:" + (googleCalendar != null && googleCalendar.isInitialized() ? "✅" : "❌")
			+ " | Naver:"  + (naverCalendar  != null && naverCalendar.isInitialized()  ? "✅" : "❌"));
			
            if (googleCalendar != null && googleCalendar.isInitialized()) {
                try {
                    for (GoogleCalendarService.CalendarEvent ev :
						googleCalendar.getUpcomingAlarms(ALARM_WINDOW_MINUTES)) {
                        if (ev.allDay) continue;
                        String key = "G_" + ev.id;
                        if (isCooldown(key)) continue;
                        long diff = java.time.Duration.between(now, ev.startTime).toMinutes();
                        if (diff >= 0 && diff <= ALARM_WINDOW_MINUTES) {
                            firedAlarms.put(key, System.currentTimeMillis());
                            fireAlarm("[구글]", ev.title, ev.startTime);
						}
					}
					} catch (Exception e) {
                    System.out.println("[CalPoller] 구글 알람 체크 오류: " + e.getMessage());
				}
			}
			
            if (naverCalendar != null && naverCalendar.isInitialized()) {
                try {
                    for (NaverCalendarService.CalendarEvent ev :
						naverCalendar.getUpcomingAlarms(ALARM_WINDOW_MINUTES)) {
                        if (ev.allDay) continue;
                        String key = "N_" + ev.id;
                        if (isCooldown(key)) continue;
                        long diff = java.time.Duration.between(now, ev.startTime).toMinutes();
                        if (diff >= 0 && diff <= ALARM_WINDOW_MINUTES) {
                            firedAlarms.put(key, System.currentTimeMillis());
                            fireAlarm("[네이버]", ev.title, ev.startTime);
						}
					}
					} catch (Exception e) {
                    System.out.println("[CalPoller] 네이버 알람 체크 오류: " + e.getMessage());
				}
			}
			
            cleanupFiredAlarms();
		}
		
        private boolean isCooldown(String key) {
            Long last = firedAlarms.get(key);
            if (last == null) return false;
            return (System.currentTimeMillis() - last) / 60000 < COOLDOWN_MINUTES;
		}
		
        // ── 알람 발동 ─────────────────────────────────────────────
        private void fireAlarm(String source, String title, ZonedDateTime startTime) {
            String msg = "📅 캘린더 알람 " + source
			+ "\n─────────────────\n"
			+ "⏰ " + startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
			+ "\n" + title;
            System.out.println("[CalPoller] 알람 발동: " + source + " " + title);
            sendTelegram(msg);
		}
		
        // ── 아침 브리핑 ───────────────────────────────────────────
        private void checkMorningBrief() {
            ZonedDateTime now      = ZonedDateTime.now();
            int           nowTotal = now.getHour() * 60 + now.getMinute();
            int           hhmm     = morningBriefTime;
            int           briefTotal = (hhmm / 100) * 60 + (hhmm % 100);
            int           day      = now.getDayOfYear();
			
            if (nowTotal >= briefTotal && nowTotal < briefTotal + 2 && day != lastBriefDay) {
                lastBriefDay = day;
                new Thread(() -> sendMorningBrief(false), "MorningBrief").start();
			}
		}
		
        public void sendStartupBrief() {
            new Thread(() -> sendMorningBrief(true), "StartupBrief").start();
		}
		
        private void sendMorningBrief(boolean isStartup) {
            System.out.println("[CalPoller] 브리핑 시작 ("
			+ (isStartup ? "시작 브리핑" : "아침 브리핑") + ")");
            try {
                List<GoogleCalendarService.CalendarEvent> googleEvents = new ArrayList<>();
                if (googleCalendar != null && googleCalendar.isInitialized())
				googleEvents = googleCalendar.getNextDays(3);
				
                List<NaverCalendarService.CalendarEvent> naverEvents = new ArrayList<>();
                if (naverCalendar != null && naverCalendar.isInitialized())
				naverEvents = naverCalendar.getNextDays(3);
				
                String        header = isStartup ? "🖥️ 앱이 시작되었습니다!" : "🌅 좋은 아침입니다!";
                StringBuilder sb     = new StringBuilder(header).append("\n\n");
				
                if (googleEvents.isEmpty() && naverEvents.isEmpty()) {
                    sb.append("📭 향후 3일간 일정이 없습니다.");
					} else {
                    if (!googleEvents.isEmpty())
					sb.append(GoogleCalendarService.formatEvents("구글 3일 일정", googleEvents));
                    if (!naverEvents.isEmpty()) {
                        if (!googleEvents.isEmpty()) sb.append("\n\n");
                        sb.append(NaverCalendarService.formatEvents("네이버 3일 일정", naverEvents));
					}
				}
				
                System.out.println("[CalPoller] 브리핑 완료 (구글:" + googleEvents.size()
				+ "건 / 네이버:" + naverEvents.size() + "건)");
                sendTelegram(sb.toString());
				
				} catch (Exception e) {
                System.out.println("[CalPoller] 브리핑 오류: " + e.getMessage());
			}
		}
		
        // ── 텔레그램 전송 ─────────────────────────────────────────
        private void sendTelegram(String msg) {
            // if (telegramBot == null) return;
            // if (telegramChatId.isEmpty()) return;
            new Thread(() -> telegramBot.sendTelegram(msg), "CalTelegram").start();
		}
		
        // ── 유틸 ──────────────────────────────────────────────────
        private void cleanupFiredAlarms() {
            long now = System.currentTimeMillis();
            firedAlarms.entrySet().removeIf(e -> (now - e.getValue()) > 3_600_000);
		}
	}
}
