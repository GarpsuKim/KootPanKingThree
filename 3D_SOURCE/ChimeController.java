import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * ChimeController (JavaFX 버전) - 차임벨 재생 로직 + 설정 다이얼로그
 *
 * ── 책임 ────────────────────────────────────────────────────
 *   ① JavaFX Timeline으로 매 초 시각 체크 → 지정 분에 차임벨 자동 재생
 *   ② javax.sound.sampled Clip 으로 WAV 재생 (볼륨 제어)
 *   ③ wmplayer 프로세스로 mp3/wma/mp4 등 재생
 *   ④ showChimeDialog() — JavaFX Stage 기반 설정 UI
 *
 * ── HostCallback ─────────────────────────────────────────────
 *   최소 인터페이스: isChild() / getTimeZone() 만 필요.
 *   (무지개 기능은 추후 추가 예정 — 현재 미구현)
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   ChimeController chime = new ChimeController(ownerStage, hostCallback);
 *   chime.startCheckTimer();      // initUI 완료 후 1회 호출
 *   chime.showChimeDialog();      // 팝업 메뉴 → 차임벨 설정
 *   chime.stopChime();            // 강제 중지
 *   chime.playMediaFile(file);    // 텔레그램 수신 미디어 재생
 *
 *   // INI 저장/로드
 *   isEnabled() getFile() getDuration() getMinutes() getVolume()
 *   setEnabled() setFile() setDuration() setMinutes() setVolume()
 */
public class ChimeController {

    // ── 호스트 콜백 인터페이스 ───────────────────────────────
    public interface HostCallback {
        /** 자식 인스턴스 여부 (자식은 차임벨 비활성) */
        boolean isChild();
        /** 현재 타임존 (차임벨 시각 체크에 사용) */
        ZoneId getTimeZone();
        /**
         * 레인보우 베젤 효과 시작.
         * @param durationSec 지속 시간(초). 0=무한(toggleMode). 30=차임벨 연동.
         */
        default void startRainbow(int durationSec) {}
    }

    // ── 설정 필드 ────────────────────────────────────────────
    private boolean   enabled  = false;
    private String    file     = "";
    /** 연주 시간: 0=처음 15초, 1=처음 30초, 2=끝까지 */
    private int       duration = 0;
    private boolean[] minutes  = new boolean[60];
    /** 볼륨: 0(무음) ~ 100(최대), 기본 80 */
    private int       volume   = 80;

    // ── 내부 상태 ────────────────────────────────────────────
    private Process          chimeProcess    = null;
    private Thread           wavThread       = null;
    private volatile boolean wavRunning      = false;
    private Timeline         checkTimer      = null;
    private int              lastChimeMinute = -1;

    // ── 의존성 ───────────────────────────────────────────────
    private final Stage        ownerStage;
    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────
    public ChimeController(Stage ownerStage, HostCallback host) {
        this.ownerStage = ownerStage;
        this.host       = host;
        minutes[0]      = true;  // 기본값: 정각(0분)
    }

    // ── 설정 접근자 ──────────────────────────────────────────

    public boolean   isEnabled()   { return enabled; }
    public String    getFile()     { return file; }
    public int       getDuration() { return duration; }
    public boolean[] getMinutes()  { return minutes; }
    public int       getVolume()   { return volume; }

    public void setEnabled(boolean v)   { this.enabled  = v; }
    public void setFile(String v)       { this.file     = (v != null) ? v : ""; }
    public void setDuration(int v)      { this.duration = (v >= 0 && v <= 2) ? v : 0; }
    public void setMinutes(boolean[] v) {
        if (v != null && v.length == 60) System.arraycopy(v, 0, minutes, 0, 60);
    }
    public void setVolume(int v)        { this.volume   = Math.max(0, Math.min(100, v)); }

    // ── 공개 API ─────────────────────────────────────────────

    /** 매 초 시각 체크 타이머 시작 (JavaFX Timeline — FX 스레드에서 실행) */
    public void startCheckTimer() {
        if (checkTimer != null) checkTimer.stop();
        checkTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> checkAndPlay()));
        checkTimer.setCycleCount(Timeline.INDEFINITE);
        checkTimer.play();
    }

    /** 차임벨 강제 중지 */
    public void stopChime() {
        wavRunning = false;
        if (wavThread  != null) { wavThread.interrupt();  wavThread  = null; }
        if (chimeProcess != null) { chimeProcess.destroy(); chimeProcess = null; }
    }

    /** 텔레그램 수신 미디어 파일을 OS 기본 앱으로 재생 */
    public void playMediaFile(File mediaFile) {
        try {
            java.awt.Desktop.getDesktop().open(mediaFile);
            System.out.println("[MediaPlay] 기본 앱으로 재생: " + mediaFile.getName());
        } catch (Exception ex) {
            System.out.println("[MediaPlay] 재생 오류: " + ex.getMessage());
        }
    }

    /**
     * 차임벨 설정 다이얼로그 (JavaFX Stage, APPLICATION_MODAL).
     * showAndWait() 사용 → 반환 후 호출자에서 saveConfig() 호출.
     */
    public void showChimeDialog() {
        Stage dlg = new Stage();
        // ── 마우스 독점 해제 ─────────────────────────────────
        // mainStage 가 StageStyle.TRANSPARENT 전체 오버레이이므로
        // initOwner + APPLICATION_MODAL 조합은 OS 마우스 이벤트를 완전히 독점해
        // 모든 윈도우가 먹통이 된다.
        // 설정 패널과 동일하게: initOwner 없음 + Modality.NONE + alwaysOnTop.
        dlg.initModality(Modality.NONE);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("차임벨 설정");
        dlg.setResizable(false);

        // ── 상단: 기본 설정 GridPane ─────────────────────────
        GridPane top = new GridPane();
        top.setHgap(8); top.setVgap(7);
        top.setPadding(new Insets(10, 10, 10, 10));
        top.setStyle("-fx-border-color: gray; -fx-border-radius: 4; -fx-border-width: 1;");

        // Row 0: on/off
        top.add(lbl("차임벨:"), 0, 0);
        CheckBox onOffBox = new CheckBox("사용");
        onOffBox.setSelected(enabled);
        GridPane.setColumnSpan(onOffBox, 3);
        top.add(onOffBox, 1, 0);

        // Row 1: 파일 선택
        top.add(lbl("파일:"), 0, 1);
        TextField fileField = new TextField(file);
        fileField.setEditable(false);
        fileField.setPrefWidth(250);
        GridPane.setHgrow(fileField, Priority.ALWAYS);
        GridPane.setColumnSpan(fileField, 2);
        top.add(fileField, 1, 1);
        Button browseBtn = new Button("찾기...");
        top.add(browseBtn, 3, 1);

        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("오디오/비디오 파일 선택");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                    "미디어 파일 (mp3, wav, wma, mp4, avi, wmv, m4a, flac, ogg)",
                    "*.mp3","*.wav","*.wma","*.mp4","*.avi","*.wmv",
                    "*.m4a","*.flac","*.ogg","*.aac","*.mkv"),
                new FileChooser.ExtensionFilter("모든 파일", "*.*")
            );
            if (!file.isEmpty()) {
                File init = new File(file);
                if (init.getParentFile() != null && init.getParentFile().exists())
                    fc.setInitialDirectory(init.getParentFile());
            }
            File chosen = fc.showOpenDialog(dlg);
            if (chosen != null) fileField.setText(chosen.getAbsolutePath());
        });

        // Row 2: 테스트 / 중지 버튼
        Button testBtn = new Button("▶ 테스트");
        Button stopBtn = new Button("■ 중지");
        HBox testRow = new HBox(8, testBtn, stopBtn);
        GridPane.setColumnSpan(testRow, 4);
        top.add(testRow, 0, 2);
        stopBtn.setOnAction(e -> stopChime());

        // Row 3: 연주 시간 라디오
        top.add(lbl("연주 시간:"), 0, 3);
        ToggleGroup tg    = new ToggleGroup();
        RadioButton r15   = new RadioButton("처음 15초만"); r15.setToggleGroup(tg);
        RadioButton r30   = new RadioButton("처음 30초만"); r30.setToggleGroup(tg);
        RadioButton rFull = new RadioButton("끝까지");      rFull.setToggleGroup(tg);
        if      (duration == 2) rFull.setSelected(true);
        else if (duration == 1) r30.setSelected(true);
        else                    r15.setSelected(true);
        top.add(r15,   1, 3);
        top.add(r30,   2, 3);
        top.add(rFull, 3, 3);

        // Row 4: 볼륨 슬라이더
        top.add(lbl("볼륨:"), 0, 4);
        Slider volSlider = new Slider(0, 100, volume);
        volSlider.setMajorTickUnit(25);
        volSlider.setMinorTickCount(4);
        volSlider.setShowTickLabels(true);
        volSlider.setShowTickMarks(true);
        volSlider.setPrefWidth(210);
        GridPane.setColumnSpan(volSlider, 2);
        top.add(volSlider, 1, 4);
        Label volLabel = new Label(volume + "%");
        volLabel.setMinWidth(40);
        top.add(volLabel, 3, 4);
        volSlider.valueProperty().addListener((obs, o, n) ->
            volLabel.setText((int) Math.round(n.doubleValue()) + "%"));

        // 테스트 버튼 리스너 (volSlider/rFull/r30 선언 후 등록)
        testBtn.setOnAction(e -> {
            String f = fileField.getText().trim();
            if (f.isEmpty()) { showAlert(dlg, "파일을 먼저 선택하세요."); return; }
            file     = f;
            volume   = (int) Math.round(volSlider.getValue());
            duration = rFull.isSelected() ? 2 : r30.isSelected() ? 1 : 0;
            playChimeInternal();
        });

        // ── 중앙: 분 체크박스 60개 (10행 × 6열) ─────────────
        GridPane minGrid = new GridPane();
        minGrid.setHgap(4); minGrid.setVgap(4);
        minGrid.setPadding(new Insets(8));
        CheckBox[] minBoxes = new CheckBox[60];
        for (int i = 0; i < 60; i++) {
            minBoxes[i] = new CheckBox(String.format("%02d분", i));
            minBoxes[i].setSelected(minutes[i]);
            minBoxes[i].setStyle("-fx-font-size:11px;");
            minGrid.add(minBoxes[i], i % 6, i / 6);
        }
        TitledPane minTitled = new TitledPane("연주 시각 (매 시각 N분에 연주)", minGrid);
        minTitled.setCollapsible(false);
        ScrollPane minScroll = new ScrollPane(minTitled);
        minScroll.setFitToWidth(true);
        minScroll.setPrefHeight(300);

        // ── 하단: 전체선택 / 해제 / 정각 | 확인 / 취소 ──────
        Button allBtn    = new Button("전체 선택");
        Button noneBtn   = new Button("선택 해제");
        Button topBtn    = new Button("정각(0분)");
        Button okBtn     = new Button("  확인  ");
        Button cancelBtn = new Button("  취소  ");

        allBtn.setOnAction(e  -> { for (CheckBox cb : minBoxes) cb.setSelected(true); });
        noneBtn.setOnAction(e -> { for (CheckBox cb : minBoxes) cb.setSelected(false); });
        topBtn.setOnAction(e  -> {
            for (CheckBox cb : minBoxes) cb.setSelected(false);
            minBoxes[0].setSelected(true);
        });
        okBtn.setOnAction(e -> {
            enabled  = onOffBox.isSelected();
            file     = fileField.getText().trim();
            duration = rFull.isSelected() ? 2 : r30.isSelected() ? 1 : 0;
            volume   = (int) Math.round(volSlider.getValue());
            for (int i = 0; i < 60; i++) minutes[i] = minBoxes[i].isSelected();
            dlg.close();
        });
        cancelBtn.setOnAction(e -> dlg.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bot = new HBox(6, allBtn, noneBtn, topBtn, spacer, okBtn, cancelBtn);
        bot.setPadding(new Insets(8, 10, 10, 10));
        bot.setAlignment(Pos.CENTER_LEFT);

        // ── 루트 ─────────────────────────────────────────────
        Label topLabel = new Label("기본 설정");
        topLabel.setStyle("-fx-font-weight:bold; -fx-padding: 0 0 2 0;");

        VBox root = new VBox(6, topLabel, top, minScroll, bot);
        root.setPadding(new Insets(8));

        dlg.setScene(new Scene(root, 465, 570));
        dlg.showAndWait();
    }

    // ── 내부: 시각 체크 (FX 스레드에서 호출) ────────────────

    private void checkAndPlay() {
        if (!enabled || file.isEmpty()) return;
        ZonedDateTime now = ZonedDateTime.now(host.getTimeZone());
        int min = now.getMinute();
        int sec = now.getSecond();
        if (sec == 0 && minutes[min] && min != lastChimeMinute) {
            lastChimeMinute = min;
            System.out.println("[Chime] ★ 차임벨 발동 → "
                + now.getHour() + "시 " + min + "분");
            playChimeInternal();
        }
        if (sec > 2) lastChimeMinute = -1;
    }

    // ── 내부: 재생 분기 ──────────────────────────────────────

    private void playChimeInternal() {
        stopChime();

        final String snapFile     = file;
        final int    snapVolume   = volume;
        final int    snapDuration = duration;

        boolean isWav = snapFile.toLowerCase().endsWith(".wav");
        System.out.println("[Chime] 재생 → " + snapFile
            + " (wav=" + isWav + ", vol=" + snapVolume
            + ", dur=" + snapDuration + ")");

        if (isWav) {
            playWavWithVolume(snapFile, snapVolume, snapDuration);
        } else {
            playWithWmplayer(snapFile, snapDuration);
        }

        // ── 차임벨 울릴 때 레인보우 30초 병행 ─────────────────
        host.startRainbow(30);
    }

    // ── 내부: WAV 재생 (javax.sound.sampled Clip) ────────────

    private void playWavWithVolume(final String playFile,
                                   final int    playVolume,
                                   final int    playDuration) {
        wavRunning = true;
        wavThread = new Thread(() -> {
            try {
                System.out.println("[Chime] WAV 로드: " + playFile);
                javax.sound.sampled.AudioInputStream ais =
                    javax.sound.sampled.AudioSystem.getAudioInputStream(new File(playFile));

                // 비PCM → PCM_SIGNED 자동 변환
                javax.sound.sampled.AudioFormat baseFmt = ais.getFormat();
                System.out.println("[Chime] WAV 포맷: " + baseFmt.getEncoding()
                    + " " + (int) baseFmt.getSampleRate() + "Hz "
                    + baseFmt.getChannels() + "ch");
                if (!baseFmt.getEncoding().equals(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED)
                 && !baseFmt.getEncoding().equals(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED)) {
                    System.out.println("[Chime] 비PCM → PCM_SIGNED 변환");
                    javax.sound.sampled.AudioFormat pcmFmt =
                        new javax.sound.sampled.AudioFormat(
                            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                            baseFmt.getSampleRate(), 16,
                            baseFmt.getChannels(), baseFmt.getChannels() * 2,
                            baseFmt.getSampleRate(), false);
                    ais = javax.sound.sampled.AudioSystem.getAudioInputStream(pcmFmt, ais);
                }

                javax.sound.sampled.Clip clip =
                    javax.sound.sampled.AudioSystem.getClip();
                clip.open(ais);
                System.out.println("[Chime] Clip 오픈 성공, 길이="
                    + clip.getMicrosecondLength() / 1000 + "ms");

                // 볼륨 적용
                if (clip.isControlSupported(
                        javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    javax.sound.sampled.FloatControl gain =
                        (javax.sound.sampled.FloatControl) clip.getControl(
                            javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    float maxDb = gain.getMaximum();
                    float minDb = gain.getMinimum();
                    float dB;
                    if      (playVolume == 0)   dB = minDb;
                    else if (playVolume >= 100) dB = maxDb;
                    else { dB = maxDb - (100 - playVolume) * 0.4f; dB = Math.max(minDb, dB); }
                    gain.setValue(dB);
                    System.out.println("[Chime] 볼륨=" + playVolume + "% → "
                        + String.format("%.1f", gain.getValue())
                        + "dB (max=" + String.format("%.1f", maxDb) + "dB)");
                }

                clip.start();
                System.out.println("[Chime] clip.start() 완료");
                Thread.sleep(50);

                long stopMs  = (playDuration == 0) ? 15_000L
                             : (playDuration == 1) ? 30_000L
                             : Long.MAX_VALUE;
                long startMs = System.currentTimeMillis();
                while (wavRunning && (clip.isActive() || clip.isRunning())) {
                    if (System.currentTimeMillis() - startMs >= stopMs) break;
                    Thread.sleep(100);
                }
                clip.stop(); clip.close(); ais.close();
                System.out.println("[Chime] 재생 완료");

            } catch (Exception ex) {
                System.out.println("[Chime] WAV 오류: " + ex.getMessage());
                Platform.runLater(() ->
                    showAlert(ownerStage, "WAV 재생 오류:\n" + ex.getMessage()));
            } finally {
                wavRunning = false;
                wavThread  = null;
            }
        }, "ChimeWav");
        wavThread.setDaemon(true);
        wavThread.start();
    }

    // ── 내부: wmplayer 재생 ──────────────────────────────────

    private void playWithWmplayer(final String playFile, final int playDuration) {
        try {
            String wmplayer = "C:\\Program Files\\Windows Media Player\\wmplayer.exe";
            if (!new File(wmplayer).exists())
                wmplayer = "C:\\Program Files (x86)\\Windows Media Player\\wmplayer.exe";

            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(wmplayer);
            if (playDuration < 2) { cmd.add("/play"); cmd.add("/close"); }
            cmd.add(playFile);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            chimeProcess = pb.start();

            // duration 제한: 백그라운드 스레드에서 sleep 후 FX 스레드로 stopChime
            if (playDuration < 2) {
                final int stopMs = (playDuration == 1) ? 30_000 : 15_000;
                new Thread(() -> {
                    try { Thread.sleep(stopMs); } catch (InterruptedException ignored) {}
                    Platform.runLater(this::stopChime);
                }, "ChimeWmpStop").start();
            }
        } catch (Exception ex) {
            System.out.println("[Chime] wmplayer 오류: " + ex.getMessage());
            Platform.runLater(() ->
                showAlert(ownerStage, "wmplayer 실행 오류:\n" + ex.getMessage()));
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────

    private static Label lbl(String text) { return new Label(text); }

    private static void showAlert(javafx.stage.Window owner, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
