public class Main{
	public static void main(String [] args) {
		System.loadLibrary("threading");
		for (int a = 0; a < 10; a++){
			new Thread().start();	
		}
	}
}
