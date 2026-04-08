/**
 * AppMenuBuilder
 * ──────────────────────────────────────────────────────────────────────────
 * KootPanKingThree.addAppMenuItems() 에서 분리한 팝업 메뉴 조립 클래스.
 *
 * 역할 분리 원칙:
 *   - AppMenuBuilder : 메뉴 구조(순서 · 구분선) 결정 + top-5 공용 항목 생성
 *   - HostCallback   : 도메인별 서브메뉴 구축 + 앱 동작(저장·이동 등) 위임
 *
 * Top-5 (child 시계에서도 표시) 고정 순서:
 *   [흔들림 — FxGPUNeon]  [중앙 고정]  [디지탈 on/off]  [디지탈 시계 설정]  [메인 시계 설정]
 * ──────────────────────────────────────────────────────────────────────────
 */
class AppMenuBuilder {

    // ── HostCallback 인터페이스 ──────────────────────────────────────────
    interface HostCallback {

        // --- 상태 조회 ---
        boolean isChild();
        FxGPUNeon.ClockController getClockController();
        boolean getDigitalState();

        // --- 상태 변경 ---
        void setDigitalState(boolean on);
        /** digitalMenuItem 필드 참조 저장 (onPopupShowing 동기화용) */
        void setDigitalMenuItemRef(javafx.scene.control.CheckMenuItem item);

        // --- 동작 ---
        void showDigitalSettingsDialog(javafx.stage.Stage owner);
        void saveConfig();
        void resetToCenter();
        /** chimeController 최초 1회 초기화 (Stage 확정 후) */
        void initChimeControllerIfNeeded(javafx.stage.Stage owner);

        // --- 서브메뉴 팩토리 (도메인 로직은 KootPanKingThree 가 소유) ---
        javafx.scene.control.MenuItem buildChimeMenuItem();
        javafx.scene.control.Menu buildWorldClockMenu();
        javafx.scene.control.Menu buildPhoneCamMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildYtMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildLocalMp4Menu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildCctvMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildGmailMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildKakaoMenu();
        javafx.scene.control.Menu buildTelegramMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildLifeMenu(javafx.scene.control.ContextMenu popup);
        javafx.scene.control.Menu buildSystemMenu(javafx.scene.control.ContextMenu popup);

        // --- 스타일 헬퍼 ---
        void enhancePopupMenuNeon(javafx.scene.control.ContextMenu popup);
        void restoreParentPopupTextVisible(javafx.scene.control.ContextMenu popup);
        void applyNeonStylesRecursively(java.util.List<javafx.scene.control.MenuItem> items);
        void emphasizePopupProgramTitle(javafx.scene.control.ContextMenu popup);
        void enhancePopupSubMenusMacOnly(javafx.scene.control.ContextMenu popup);
    }

    // ── 생성자 ──────────────────────────────────────────────────────────
    private final HostCallback host;

    AppMenuBuilder(HostCallback host) {
        this.host = java.util.Objects.requireNonNull(host, "HostCallback must not be null");
    }

    // ── 메인 빌드 ────────────────────────────────────────────────────────
    /**
     * popup 에 모든 메뉴 항목을 순서대로 추가한다.
     * FxGPUNeon.buildGraphicsMenu() 가 header / separator / 흔들림을
     * 먼저 추가한 상태에서 호출된다.
     *
     * 최종 팝업 순서:
     *   [흔들림]                     ← FxGPUNeon
     *   [중앙 고정]                  ← 여기서 추가
     *   [디지탈 on/off]              ← 여기서 추가
     *   [디지탈 시계 설정]           ← 여기서 추가
     *   [메인 시계 설정]             ← 여기서 추가
     *   ─────────────────
     *   [세계 시계] (로컬만)
     *   ─────────────────
     *   [📷 스마트폰 카메라]
     *   [▶ YouTube 실시간 세계도시]
     *   [📂 로컬 MP4 배경 재생]
     *   [🚦 ITS 교통 CCTV]
     *   ─────────────────
     *   [🔔 차임벨 설정]
     *   ─────────────────
     *   [📧 Gmail / Calendar]
     *   [카카오톡]
     *   [텔레그램]
     *   ─────────────────
     *   [생활도구]
     *   ─────────────────
     *   [시스템]
     */
    void build(javafx.scene.control.ContextMenu popup) {

        // ── 스타일 초기화 ────────────────────────────────────────────────
        host.enhancePopupMenuNeon(popup);
        host.restoreParentPopupTextVisible(popup);

        // ── chimeController 최초 초기화 ──────────────────────────────────
        javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
        host.initChimeControllerIfNeeded(owner);

        // ── Top-5 공용 항목 생성 ─────────────────────────────────────────

        // [중앙 고정]
        javafx.scene.control.MenuItem centerItem =
            new javafx.scene.control.MenuItem("📌 중앙 고정");
        centerItem.setOnAction(e -> host.resetToCenter());

        // [디지탈 on/off]
        javafx.scene.control.CheckMenuItem digitalItem =
            new javafx.scene.control.CheckMenuItem("🕐 디지탈 on/off");
        FxGPUNeon.ClockController cc = host.getClockController();
        digitalItem.setSelected(cc != null && host.getDigitalState());
        digitalItem.setOnAction(e -> {
            host.setDigitalState(digitalItem.isSelected());
            host.saveConfig();
        });
        // 팝업 열릴 때마다 실제 상태로 동기화
        host.setDigitalMenuItemRef(digitalItem);
        if (cc != null) {
            cc.onPopupShowing = () -> digitalItem.setSelected(host.getDigitalState());
        }

        // [디지탈 시계 설정]
        javafx.scene.control.MenuItem digitalSettingsItem =
            new javafx.scene.control.MenuItem("디지탈 시계 설정");
        digitalSettingsItem.setOnAction(e ->
            host.showDigitalSettingsDialog((javafx.stage.Stage) popup.getOwnerWindow()));

        // [메인 시계 설정]
        javafx.scene.control.MenuItem menuSetup =
            new javafx.scene.control.MenuItem("메인 시계 설정");
        menuSetup.setOnAction(e -> {
            FxGPUNeon.ClockController c = host.getClockController();
            if (c != null) c.openSetup();
        });

        // ── 팝업 조립 ────────────────────────────────────────────────────

        // (A) Top-5 + separator
        popup.getItems().addAll(
            centerItem,
            digitalItem,
            digitalSettingsItem,
            menuSetup,
            new javafx.scene.control.SeparatorMenuItem()
        );

        // (B) 세계시계 (로컬 시계만)
        if (!host.isChild()) {
            popup.getItems().addAll(
                host.buildWorldClockMenu(),
                new javafx.scene.control.SeparatorMenuItem()
            );
        }

        // (C) 미디어 배경
        popup.getItems().addAll(
            host.buildPhoneCamMenu(popup),
            host.buildYtMenu(popup),
            host.buildLocalMp4Menu(popup),
            host.buildCctvMenu(popup),
            new javafx.scene.control.SeparatorMenuItem()
        );

        // (D) 차임벨
        popup.getItems().addAll(
            host.buildChimeMenuItem(),
            new javafx.scene.control.SeparatorMenuItem()
        );

        // (E) 커뮤니케이션
        popup.getItems().addAll(
            host.buildGmailMenu(popup),
            host.buildKakaoMenu(),
            host.buildTelegramMenu(popup),
            new javafx.scene.control.SeparatorMenuItem()
        );

        // (F) 생활도구
        popup.getItems().addAll(
            host.buildLifeMenu(popup),
            new javafx.scene.control.SeparatorMenuItem()
        );

        // (G) 시스템
        popup.getItems().add(host.buildSystemMenu(popup));

        // ── 스타일 마감 ──────────────────────────────────────────────────
        host.applyNeonStylesRecursively(popup.getItems());
        host.restoreParentPopupTextVisible(popup);
        host.emphasizePopupProgramTitle(popup);
        host.enhancePopupSubMenusMacOnly(popup);
    }
}
