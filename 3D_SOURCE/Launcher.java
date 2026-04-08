// Launcher.java (Application 미상속, 순수 일반 클래스)
public class Launcher {
    private static final String thisProgramName = "[KootPanKingThree 3차원_끝판왕 (v1.0)]";
    public static void main(String[] args) {        
		AppLogger.init();
        AppLogger.writeToFile("[ " + thisProgramName + " ] [main] 시작");
		
        KootPanKingThree.fxMain(args);		
		
        System.out.println("[ " + thisProgramName + " ] [main] bye bye");
        AppLogger.close();
    }
}