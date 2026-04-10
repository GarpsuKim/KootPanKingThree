import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

/**
 * IniController
 *
 * 책임:
 * 1) settings 폴더 보장
 * 2) 구버전 ini를 settings 폴더로 이동
 * 3) ini가 없으면 기본 ini 생성(부모 인스턴스만 GitHub 다운로드)
 * 4) Properties load/save/open 전담
 *
 * KootPanKingThree 는 이 클래스를 호출만 한다.
 */
public final class IniController {

    private static final String DEFAULT_INI_URL =
        "https://raw.githubusercontent.com/GarpsuKim/KootPanKingThree/refs/heads/main/INI_bak/clock_settings_default.ini";

    private final String appDir;
    private final String settingsDir;
    private final String configFilePath;
    private final String cityName;
    private final Properties config = new Properties();

    public IniController(String appDir, String settingsDir, String configFilePath,String cityName) {
        this.appDir = appDir;
        this.settingsDir = settingsDir;
        this.configFilePath = configFilePath;
        this.cityName = cityName != null ? cityName : "";
        System.out.println("[IniController] appDir = [" + appDir + "]");    
        System.out.println("[IniController] settingsDir = [" + settingsDir + "]");    
        System.out.println("[IniController] configFilePath = [" + configFilePath + "]");    
        System.out.println("[IniController] cityName = [" + cityName + "]");    
    }

    public boolean exists() {
        return new File(configFilePath).exists();
    }

    public void initialize() {
        ensureSettingsDir();
        // migrateLegacyIniIfNeeded();
        ensureIniExists();
    }

    public boolean ensureInitialized() {
        initialize();
        return exists();
    }

    public void load() {
        File f = new File(configFilePath);
        System.out.println("[IniController] load 경로: " + f.getAbsolutePath());
        if (!f.exists()) {
            System.out.println("[IniController] load 스킵 - ini 없음");
            return;
        }

        try (FileInputStream fis = new FileInputStream(f)) {
            config.clear();
            config.load(fis);
        } catch (Exception e) {
            System.out.println("[IniController] load 실패: " + e.getMessage());
        }
    }

    public void save() {
        try (FileOutputStream fos = new FileOutputStream(configFilePath)) {
            config.store(fos, "KootPanKingThree Settings");
        } catch (Exception e) {
            System.out.println("[IniController] save 실패: " + e.getMessage());
        }
    }

    public void open() {
        try {
            File f = new File(configFilePath);
            if (!f.exists()) {
                System.out.println("[IniController] 설정 파일 없음: " + f.getAbsolutePath());
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(f);
                } catch (Exception ex) {
                    desktop.browse(f.toURI());
                }
            }
        } catch (Exception e) {
            System.out.println("[IniController] 설정 파일 열기 실패: " + e.getMessage());
        }
    }

    public Properties getProperties() {
        return config;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public static String getDefaultSettingsDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        return appData + File.separator + "KootPanKingThree"
                + File.separator + "settings" + File.separator;
    }

    public static String getPrimaryConfigFilePath() {
        return getDefaultSettingsDir() + "clock_settings.ini";
    }

    public static boolean primaryConfigExists() {
        return new File(getPrimaryConfigFilePath()).exists();
    }

    public static boolean ensurePrimaryConfigFile() {
        IniController controller = new IniController(
                "",
                getDefaultSettingsDir(),
                getPrimaryConfigFilePath(),
                "Local");
        return controller.ensureInitialized();
    }

    public static void openPrimaryConfigFile() {
        IniController controller = new IniController(
                "",
                getDefaultSettingsDir(),
                getPrimaryConfigFilePath(),
                "Local");
        controller.initialize();
        controller.open();
    }

    private void ensureSettingsDir() {
        File s = new File(settingsDir);
        if (!s.exists()) s.mkdirs();
    }
/*
    private void migrateLegacyIniIfNeeded() {
        File target = new File(configFilePath);

        if (!isChild) {
            File oldIni = new File(appDir + "clock_settings.ini");
            if (oldIni.exists() && !oldIni.getAbsolutePath().equals(target.getAbsolutePath())) {
                if (oldIni.renameTo(target)) {
                    System.out.println("[IniController] 구버전 ini 이동 완료: " + oldIni.getName());
                }
            }
            return;
        }
        String safeName = cityName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safeName.isEmpty()) return;

        File oldChild = new File(appDir + "clock_settings_" + safeName + ".ini");
        if (oldChild.exists() && !oldChild.getAbsolutePath().equals(target.getAbsolutePath())) {
            if (oldChild.renameTo(target)) {
                System.out.println("[IniController] 구버전 자식 ini 이동 완료: " + oldChild.getName());
            }
        }
    }
	*/
    private void ensureIniExists() {
        File f = new File(configFilePath);
        if (f.exists()) {
            System.out.println("[IniController] ini 존재: " + f.getAbsolutePath());
            return;
        }
        downloadDefaultConfig(f);
        if (f.exists()) {
            System.out.println("[IniController] ini 준비 완료: " + f.getAbsolutePath());
        } else {
            System.out.println("[IniController] ini 준비 실패 - 코드 기본값 사용");
        }
    }
    private void downloadDefaultConfig(File destFile) {
        System.out.println("[IniController] 기본 설정 파일 다운로드 시도: " + DEFAULT_INI_URL);
        try {
            URL url = new URI(DEFAULT_INI_URL).toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.connect();
            if (con.getResponseCode() != 200) {
                System.out.println("[IniController] 다운로드 실패 (HTTP " + con.getResponseCode() + ")");
                con.disconnect();
                return;
            }
            try (InputStream in = con.getInputStream();
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            con.disconnect();
            System.out.println("[IniController] 기본 설정 파일 다운로드 완료: " + destFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("[IniController] 다운로드 오류: " + e.getMessage());
        }
    }
}
