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
 * ChimeController (JavaFX Version) - Chime벨 재생 로직 + Settings 다이얼로그
 *
 * ── 재생 분기 ─────────────────────────────────────────────────────
 *   ① WAV  → javax.sound.sampled Clip (볼륨 제어)
 *   ② Video(mp4/avi/wmv/mkv/mov/m4v) → BackgroundPlayer.YoutubePlayer.startLocalMp4()
 *      · ffmpeg 있으면: rawvideo File이프 → 코인 페이스 주입
 *      · ffmpeg 없으면: JavaFX MediaPlayer 폴백
 *      · duration 0/1 → 지정 초 후 자동 stop / 2 → 영상 Full playback
 *   ③ 나머지 오디오(mp3/wma/m4a/flac/ogg) → wmplayer 프로세스 (기존 유지)
 *
 * ── HostCallback 변경 ─────────────────────────────────────────────
 *   getVideoPlayer() — BackgroundPlayer.YoutubePlayer 인스턴스 반환.
 *   Video Chime벨 기능이 필요 없으면 default(null) 반환해도 무방.
 *   null이면 자동으로 wmplayer 폴백.
 */
public class ChimeController {

    // ── Video 확장자 목록 ───────────────────────────────────────
    private static final java.util.Set<String> VIDEO_EXTS = new java.util.HashSet<>(
        java.util.Arrays.asList("mp4","avi","wmv","mkv","mov","m4v","flv","webm")
    );

    // ── 호스트 콜백 인터페이스 ───────────────────────────────────
    public interface HostCallback {
        /** 자식 인스턴스 여부 (자식은 Chime 비활성) */
        boolean isChild();

        /** 현재 타임존 (Chime 시각 체크에 Enable) */
        ZoneId getTimeZone();

        /**
         * 레인보우 베젤 효과 시작.
         * @param durationSec 지속 시간(초). 0=무한(toggleMode). 30=Chime벨 Integrations.
         */
        default void startRainbow(int durationSec) {}

        /**
         * Video Chime벨 재생에 Enable할 YoutubePlayer 인스턴스.
         * null 반환 h wmplayer 폴백.
         */
        default BackgroundPlayer.YoutubePlayer getVideoPlayer() { return null; }

        /**
         * ffmpeg.exe 절대 Path (ini: MP4.FFMPEG).
         * Video Chime벨 재생 전 Path 진단에 Enable.
         */
        default String getFfmpegPath() { return ""; }
    }

    // ── Settings 필드 ────────────────────────────────────────────────
    private boolean   enabled  = false;
    private String    file     = "";
    /** Duration: 0=처음 15s, 1=처음 30s, 2=Full playback */
    private int       duration = 0;
    private boolean[] minutes  = new boolean[60];
    /** Volume: 0(무음) ~ 100(최대), 기본 80 */
    private int       volume   = 80;

    // ── 내부 상태 ────────────────────────────────────────────────
    private Process          chimeProcess    = null;
    private Thread           wavThread       = null;
    private volatile boolean wavRunning      = false;
    private Timeline         checkTimer      = null;
    private int              lastChimeMinute = -1;

    /** Video Playing단용 타이머 (duration 0/1 days 때) */
    private java.util.concurrent.ScheduledFuture<?> videoStopFuture = null;
    private final java.util.concurrent.ScheduledExecutorService videoStopScheduler =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChimeVideoStop");
            t.setDaemon(true);
            return t;
        });

    // ── 의존성 ───────────────────────────────────────────────────
    private final Stage        ownerStage;
    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────────
    public ChimeController(Stage ownerStage, HostCallback host) {
        this.ownerStage = ownerStage;
        this.host       = host;
        minutes[0]      = true;  // 기본값: On the hour (0m)
    }

    // ── Settings 접근자 ──────────────────────────────────────────────

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

    // ── 공개 API ─────────────────────────────────────────────────

    /** 매 s 시각 체크 타이머 시작 (JavaFX Timeline — FX 스레드에서 실행) */
    public void startCheckTimer() {
        if (checkTimer != null) checkTimer.stop();
        checkTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> checkAndPlay()));
        checkTimer.setCycleCount(Timeline.INDEFINITE);
        checkTimer.play();
    }

    /** Chime 강제 Stop */
    public void stopChime() {
        // ① WAV 중지
        wavRunning = false;
        if (wavThread    != null) { wavThread.interrupt();    wavThread    = null; }
        // ② wmplayer 프로세스 중지
        if (chimeProcess != null) { chimeProcess.destroy();   chimeProcess = null; }
        // ③ Video 예약 stop 취소
        cancelVideoStop();
        // ④ YoutubePlayer Video 중지
        BackgroundPlayer.YoutubePlayer vp = host.getVideoPlayer();
        if (vp != null && vp.isRunning()) {
            // FX 스레드에서 직접 호출 — Platform.runLater로 감싸면
            // 새 audioPlayer 생성(C) 이후에 stop cleanup(A)이 실행되어
            // 방금 만든 플레이어를 killOther는 순서 역전 버그 발생
            vp.stop();
        }
    }

    /** Telegram Wed신 Media File을 OS 기본 앱으로 재생 */
    public void playMediaFile(File mediaFile) {
        try {
            java.awt.Desktop.getDesktop().open(mediaFile);
            System.out.println("[MediaPlay] 기본 앱으로 재생: " + mediaFile.getName());
        } catch (Exception ex) {
            System.out.println("[MediaPlay] Playback error: " + ex.getMessage());
        }
    }

    /**
     * Chime벨 Settings 다이얼로그 (JavaFX Stage, APPLICATION_MODAL).
     * showAndWait() Enable → 반환 후 호출자에서 saveConfig() 호출.
     */
    public void showChimeDialog() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.NONE);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Chime Settings");
        dlg.setResizable(false);

        // ── 상단: Default Settings GridPane ─────────────────────────
        GridPane top = new GridPane();
        top.setHgap(8); top.setVgap(7);
        top.setPadding(new Insets(10, 10, 10, 10));
        top.setStyle("-fx-border-color: gray; -fx-border-radius: 4; -fx-border-width: 1;");

        // Row 0: on/off
        top.add(lbl("Chime:"), 0, 0);
        CheckBox onOffBox = new CheckBox("Enable");
        onOffBox.setSelected(enabled);
        GridPane.setColumnSpan(onOffBox, 3);
        top.add(onOffBox, 1, 0);

        // Row 1: Select File
        top.add(lbl("File:"), 0, 1);
        TextField fileField = new TextField(file);
        fileField.setEditable(false);
        fileField.setPrefWidth(250);
        GridPane.setHgrow(fileField, Priority.ALWAYS);
        GridPane.setColumnSpan(fileField, 2);
        top.add(fileField, 1, 1);
        Button browseBtn = new Button("Browse...");
        top.add(browseBtn, 3, 1);

        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Audio/Video File");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                    "Media File (mp3, wav, wma, mp4, avi, wmv, m4a, flac, ogg, mkv, mov)",
                    "*.mp3","*.wav","*.wma","*.mp4","*.avi","*.wmv",
                    "*.m4a","*.flac","*.ogg","*.aac","*.mkv","*.mov","*.m4v"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            if (!file.isEmpty()) {
                File init = new File(file);
                if (init.getParentFile() != null && init.getParentFile().exists())
                    fc.setInitialDirectory(init.getParentFile());
            }
            File chosen = fc.showOpenDialog(dlg);
            if (chosen != null) {
                fileField.setText(chosen.getAbsolutePath());
                // Video Select File h 힌트 표시
                if (isVideoFile(chosen.getName())) {
                    System.out.println("[Chime] Video Select File → BackgroundPlayer 로 재생");
                }
            }
        });

        // Row 2: 테스트 / 중지 버튼
        Button testBtn = new Button("▶ Test");
        Button stopBtn = new Button("■ Stop");
        // Video Select File h 재생 방식 힌트 라벨
        Label modeHint = new Label();
        modeHint.setStyle("-fx-font-size:10px; -fx-text-fill: #666;");
        updateModeHint(modeHint, fileField.getText());
        fileField.textProperty().addListener((obs, o, n) -> updateModeHint(modeHint, n));

        HBox testRow = new HBox(8, testBtn, stopBtn, modeHint);
        testRow.setAlignment(Pos.CENTER_LEFT);
        GridPane.setColumnSpan(testRow, 4);
        top.add(testRow, 0, 2);
        stopBtn.setOnAction(e -> stopChime());

        // Row 3: 연주 시간 라디오
        top.add(lbl("Duration:"), 0, 3);
        ToggleGroup tg    = new ToggleGroup();
        RadioButton r15   = new RadioButton("First 15s only"); r15.setToggleGroup(tg);
        RadioButton r30   = new RadioButton("First 30s only"); r30.setToggleGroup(tg);
        RadioButton rFull = new RadioButton("Full playback");      rFull.setToggleGroup(tg);
        if      (duration == 2) rFull.setSelected(true);
        else if (duration == 1) r30.setSelected(true);
        else                    r15.setSelected(true);
        top.add(r15,   1, 3);
        top.add(r30,   2, 3);
        top.add(rFull, 3, 3);

        // Row 4: 볼륨 슬라이더
        top.add(lbl("Volume:"), 0, 4);
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

        // 테스트 버튼 리스너
        testBtn.setOnAction(e -> {
            String f = fileField.getText().trim();
            if (f.isEmpty()) { showAlert(dlg, "Please select a file first."); return; }
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
            minBoxes[i] = new CheckBox(String.format("%02dm", i));
            minBoxes[i].setSelected(minutes[i]);
            minBoxes[i].setStyle("-fx-font-size:11px;");
            minGrid.add(minBoxes[i], i % 6, i / 6);
        }
        TitledPane minTitled = new TitledPane("Play Time (play at Nm each hour)", minGrid);
        minTitled.setCollapsible(false);
        ScrollPane minScroll = new ScrollPane(minTitled);
        minScroll.setFitToWidth(true);
        minScroll.setPrefHeight(300);

        // ── Other단: AllSelect / 해제 / 정각 | 확인 / 취소 ──────
        Button allBtn    = new Button("All Select");
        Button noneBtn   = new Button("Select remove");
        Button topBtn    = new Button("On the hour (0m)");
        Button okBtn     = new Button("  OK  ");
        Button cancelBtn = new Button("  Cancel  ");

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
        Label topLabel = new Label("Default Settings");
        topLabel.setStyle("-fx-font-weight:bold; -fx-padding: 0 0 2 0;");

        VBox root = new VBox(6, topLabel, top, minScroll, bot);
        root.setPadding(new Insets(8));

        dlg.setScene(new Scene(root, 465, 590));
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
            System.out.println("[Chime] ★ Chime벨 발동 → "
                + now.getHour() + "h " + min + "m");
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

        boolean isWav   = snapFile.toLowerCase().endsWith(".wav");
        boolean isVideo = isVideoFile(snapFile);

        System.out.println("[Chime] 재생 → " + snapFile
            + " (wav=" + isWav + ", video=" + isVideo
            + ", vol=" + snapVolume + ", dur=" + snapDuration + ")");

        if (isWav) {
            // ① WAV: javax.sound.sampled (볼륨 제어)
            playWavWithVolume(snapFile, snapVolume, snapDuration);

        } else if (isVideo) {
            // ② Video: BackgroundPlayer.YoutubePlayer.startLocalMp4()
            playWithLocalMp4(snapFile, snapVolume, snapDuration);

        } else {
            // ③ 오디오(mp3/wma/m4a/flac 등): wmplayer 프로세스 폴백
            playWithWmplayer(snapFile, snapDuration);
        }

        // Chime벨 울릴 때 레인보우 30초 병행
        host.startRainbow(30);
    }

    // ── ② Video: BackgroundPlayer.YoutubePlayer 통합 ───────

    /**
     * Video File을 BackgroundPlayer.YoutubePlayer.startLocalMp4()로 재생.
     * · ffmpeg 있으면 코인 페이스에 영상 주입
     * · duration 0/1 이면 지정 초 후 자동 stop
     * · duration 2 이면 영상이 자연 종료될 때까지 재생
     * FX 스레드에서 호출.
     */
    private void playWithLocalMp4(String playFile, int playVolume, int playDuration) {
        BackgroundPlayer.YoutubePlayer vp = host.getVideoPlayer();
        if (vp == null) {
            // YoutubePlayer 미제공 → wmplayer 폴백
            System.out.println("[Chime] YoutubePlayer 없음 → wmplayer 폴백");
            playWithWmplayer(playFile, playDuration);
            return;
        }

        // ── ffmpeg Path 사전 진단 ─────────────────────────────────
        String ffmpegPath = host.getFfmpegPath();
        System.out.println("[Chime-MP4] ffmpegPath=\"" + ffmpegPath + "\"");
        if (ffmpegPath == null || ffmpegPath.isEmpty()) {
            System.out.println("[Chime-MP4] ★ ffmpegPath 빈 문자열 → ini Save 여부 확인 필요");
        } else if (!new File(ffmpegPath).exists()) {
            System.out.println("[Chime-MP4] ★ ffmpeg File not found → Path 불치: " + ffmpegPath);
        } else {
            System.out.println("[Chime-MP4] ffmpeg 정상");
        }

        File mp4 = new File(playFile);
        if (!mp4.exists()) {
            System.out.println("[Chime] Video File not found: " + playFile);
            return;
        }

        // 볼륨 Apply (0~100 → 0.0~1.0)
        vp.setVolume(playVolume / 100.0);
        vp.startLocalMp4(mp4);

        System.out.println("[Chime] Video Chime벨 Start: " + mp4.getName()
            + " vol=" + playVolume + "% dur=" + playDuration);

        // duration 0(15초) / 1(30초): 지정 시간 후 자동 stop
        if (playDuration < 2) {
            long stopMs = (playDuration == 1) ? 30_000L : 15_000L;
            cancelVideoStop();
            videoStopFuture = videoStopScheduler.schedule(() ->
                Platform.runLater(() -> {
                    if (vp.isRunning()) {
                        vp.stop();
                        System.out.println("[Chime] Video Chime벨 자동 종료 ("
                            + stopMs / 1000 + "s)");
                    }
                }),
                stopMs, java.util.concurrent.TimeUnit.MILLISECONDS
            );
        }
        // duration 2(Full playback): YoutubePlayer 내부가 -stream_loop 없이 1사이클 재생 후 종료
        // startLocalMp4는 기본 setCycleCount(INDEFINITE)이므로, Full playback=무한반복 허용
    }

    private void cancelVideoStop() {
        if (videoStopFuture != null && !videoStopFuture.isDone()) {
            videoStopFuture.cancel(false);
            videoStopFuture = null;
        }
    }

    // ── ① WAV 재생 (javax.sound.sampled Clip) ────────────────

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

                // 볼륨 Apply
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
                System.out.println("[Chime] clip.start() Done");
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
                System.out.println("[Chime] 재생 Done");

            } catch (Exception ex) {
                System.out.println("[Chime] WAV 오류: " + ex.getMessage());
                Platform.runLater(() ->
                    showAlert(ownerStage, "WAV playback error:\n" + ex.getMessage()));
            } finally {
                wavRunning = false;
                wavThread  = null;
            }
        }, "ChimeWav");
        wavThread.setDaemon(true);
        wavThread.start();
    }

    // ── ③ wmplayer 폴백 (오디오 전용: mp3/wma/m4a 등) ───────

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
                showAlert(ownerStage, "wmplayer launch error:\n" + ex.getMessage()));
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────

    /**
     * File명(또는 Path)이 Video 확장자인지 확인.
     */
    private static boolean isVideoFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return false;
        return VIDEO_EXTS.contains(filePath.substring(dot + 1).toLowerCase());
    }

    /** 다이얼로그 File 필드 변경 h 재생 방식 힌트 갱신 */
    private void updateModeHint(Label hint, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            hint.setText("");
        } else if (isVideoFile(filePath)) {
            BackgroundPlayer.YoutubePlayer vp = host.getVideoPlayer();
            hint.setText(vp != null
                ? "🎬 Video → BackgroundPlayer (coin face feed)"
                : "🎬 Video → wmplayer fallback (no YoutubePlayer)");
        } else if (filePath.toLowerCase().endsWith(".wav")) {
            hint.setText("🔊 WAV → javax.sound Clip");
        } else {
            hint.setText("🎵 Audio → wmplayer");
        }
    }

    private static Label lbl(String text) { return new Label(text); }

    private static void showAlert(javafx.stage.Window owner, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
