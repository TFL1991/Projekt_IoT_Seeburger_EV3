// Diese Klasse steuert den kompletten Scan- und Abwurfprozess des
// Roboters, und sendet auch Nachrichten zur�ck an den Raspberry Pi

package colorsorter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import lejos.hardware.Audio;
import lejos.hardware.Brick;
import lejos.hardware.BrickFinder;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.Port;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.SensorMode;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;

public class ColorSorter extends Thread{
	
	// Initialisieren der ben�tigten Objekte f�r den Farbsensor
	public static EV3ColorSensor colorSensor;
	public static SampleProvider colorProvider;
	public static SensorMode color;
	
	// In diesem float-Array werden nacher die 3 Werte f�r 
	// R G und B abgelegt.
	public static float[] colorSample;
	
	// Um nacher T�ne abspielen zu k�nnen
	public final static int[] PIANO = new int[]{4, 25, 500, 7000, 5}; 
	
	// Der EV3 als Objekt, um den Sensor ansprechen zu k�nnen.
	public static Brick brick;

	
	
	public void run() {
		//LED des EV3 auf schnell blinkendes Gr�n umstellen
		Button.LEDPattern(7);
		
		// Den Farbsensor auf Port 1 registrieren
		brick = BrickFinder.getDefault();
		Port s1 = brick.getPort("S1");
		colorSensor = new EV3ColorSensor(s1);
		
		// Den Modus des Farbsensors auf RGB einstellen, anschlie�end
		// 3 Pl�tze im colorSample-Array schaffen (f�r R, G und B)
		SensorMode color = colorSensor.getRGBMode();
		colorSample = new float[3];
		
		
		// F�rderband starten, davor 3 T�ne abspielen
		Machine.feederband = new EV3LargeRegulatedMotor(MotorPort.A);
		Sound.playNote(PIANO, 150, 600);
		Delay.msDelay(400);
		Sound.playNote(PIANO, 150, 600);
		Delay.msDelay(400);
		Sound.playNote(PIANO, 150, 600);
		Delay.msDelay(1000);
		Machine.feederband.setSpeed(40);
		Machine.feederband.backward();
		System.out.println("Foerderband gestartet.");
		
		//Farbe auf gr�n blinkend stellen
		Button.LEDPattern(4);
		
		//Neuen Thread StoneSlider aufrufen, zust�ndig f�r das Auflegen der Steine auf das F�rderband
		StoneSlider a1 = new StoneSlider();
		a1.start();
		System.out.println("Auflegen der Steine aktiviert.");
				
		// Solange die Schleife nicht durch Tastendruck unterbrochen wird oder durch einen Befehl 
		// des EV3 ausgeschaltet wird: Scanne permanent alle 50ms das F�rderband.
		Machine.timewithoutstoneonfeederband = 0.0;
		while (!Thread.currentThread().isInterrupted() && Machine.machinerunning == true) {
			// Wenn zu lange kein Stein mehr kommt, Maschine abschalten!
			if (Machine.timewithoutstoneonfeederband >= Machine.automaticoff) {
				Machine.machineoutofstone = true;
				Machine.machinerunning = false;
				break;
			}
			
			// Abrufen des aktuellen Samples, ein Sample ist der RGB-Wert, der vom Sensor zu genau
			// dem Zeitpunkt des Aufrufs gemessen wurde.
			color.fetchSample(colorSample, 0);
			
			// Das Sample �berpr�fen ob es die Werte aufweist, die zu einem Stein geh�ren k�nnten.
			// Wenn das so ist, bekommt die Variable colorid je nach Farbe eine Zahl, anhand der
			// die Farbe identifiziert werden kann zugewiesen (1=Gelb, 2=Rot, 3=Blau, 4=Gr�n)
			int colorid = checkForColor(colorSample);
			
			// Wenn das aktuell gemesene Sample zu einem Stein geh�rt:
			if (colorid > 0 ) {
				
				// Z�hler f�r Zeit ohne Stein wieder resetten.
				Machine.timewithoutstoneonfeederband = 0.0;
				
				// Wenn ein Filter auf diesen Stein gesetzt ist, d.h dieser
				// auch abgeworfen werden soll:
				if (checkForDrop(colorid) == true) {	
					// Den Z�hler f�r die Steine um 1 erh�hen
					Machine.stonescounted += 1;
					
					System.out.println("Farbe "+getColorName(colorid)+" wurde erkannt. Werfe Stein ab...");
					
					// Eine Meldung an den Raspberry Pi schicken, dass ein Stein erkannt wurde, welcher nun 
					// abgeworfen wird.
					DataSender d1 = new DataSender("scannedstone", getColorName(colorid), giveRGBString(colorSample));
					d1.start();
					
					// Den Abwurf-Mechanismus starten.
			    	abwerfen(colorid);
				}
			}
			
			// Alle 50ms wird das F�rderband gescannt, wird kein Stein erkannt, wird
			// der Z�hler um 0.05 erh�ht.
			else {
				Machine.timewithoutstoneonfeederband += 0.05;
			}
		
		//K�nstliche Verz�gerung f�r das n�chste Abfragen eines Sensorwertes.
		Delay.msDelay(50);
			
		}
		stopProcess();
		this.interrupt();
	}	
		
	// Falls die Maschine per Befehl angehalten wird, halte das F�rderband an
	// und deaktiviere den Sensor. Setze die LED am EV3 auf Gelb.
	public static void stopProcess() {
		//Stoppe Sensoren und Motoren
		colorSensor.close();
		Machine.feederband.stop();
		Machine.feederband.close();
		//Sende Nachricht
		DataSender d1 = new DataSender("responsemachinestopped");
 	   	d1.start();
 	   	Button.LEDPattern(6);
 	   	
 	   	//Setzt die Variablen die eventuell ver�ndert wurden zur�ck.
 	   	
	   	Machine.dropred = Machine.dropyellow = 
		Machine.dropblue = Machine.dropgreen = false;
	   	Machine.timewithoutstoneonfeederband = 0.0;
			 
	}	
		
	
	
	//�berpr�ft ein Farb-Sample. Wenn die Daten im Farbspektrum eines Steins
	//liegen, wird der je nach Farbe ein Farbcode zur�ckgegeben.
	//(0=Kein Stein, 1=Gelb, 2=Rot, 3=Blau, 4=Gr�n)
	public static int checkForColor (float[] colorSample) {
			int colorid = -1;
			if (colorSample[0] >= 0.13 && colorSample[1] >= 0.085) {
				//Sound.playNote(PIANO, 200, 100);
				colorid = 1;
			}
			else if (colorSample[0] >= 0.07 && colorSample[0] <= 0.13 && colorSample[1] < 0.04) {
				//Sound.playNote(PIANO, 400, 100);
				colorid = 2;
			}
			else if (colorSample[0] <= 0.05 && colorSample[1] <= 0.055 && colorSample[1] >= 0.02 && colorSample[2] >= 0.035) {
				//Sound.playNote(PIANO, 600, 100);
				colorid = 3;
			}
			else if (colorSample[0] <= 0.03 && colorSample[1] <= 0.075 && colorSample[1] >= 0.04 && colorSample[2] < 0.035) {
				//Sound.playNote(PIANO, 800, 100);
				colorid = 4;
			}
			else {
				colorid = 0;
			}	
			return colorid;
		}

	//Aktiviert den Abwurfmechanismus, je nach �bergebener Farb-ID wird das F�rderband
	//schnell an die richtige Stelle gefahren und der Stein abgeworfen.
	public static void abwerfen(int farbeid) {
		Sound.playNote(PIANO, 400, 100);
		if (farbeid == 1) {
			RegulatedMotor m1 = new EV3MediumRegulatedMotor(MotorPort.C);
			m1.setSpeed(400);
			m1.rotate(60);
			m1.close();
			Machine.feederband.setSpeed(200);
			Delay.msDelay(1700);
			Machine.feederband.stop();
			RegulatedMotor m11 = new EV3MediumRegulatedMotor(MotorPort.C);
			m11.setSpeed(75);
			m11.rotate(120);
			m11.close();
			Delay.msDelay(300);
			Machine.feederband.setSpeed(40);
			Machine.feederband.backward();
		}
		else if (farbeid == 2) {
			RegulatedMotor m1 = new EV3MediumRegulatedMotor(MotorPort.C);
			m1.setSpeed(400);
			m1.rotate(-60);
			m1.close();
			Machine.feederband.setSpeed(200);
			Delay.msDelay(1700);
			Machine.feederband.stop();
			RegulatedMotor m11 = new EV3MediumRegulatedMotor(MotorPort.C);
			m11.setSpeed(75);
			m11.rotate(-120);
			m11.close();
			Delay.msDelay(300);
			Machine.feederband.setSpeed(40);
			Machine.feederband.backward();
		}
		else if (farbeid == 3) {
			RegulatedMotor m1 = new EV3MediumRegulatedMotor(MotorPort.B);
			m1.setSpeed(400);
			m1.rotate(60);
			m1.close();
			Machine.feederband.setSpeed(200);
			Delay.msDelay(2600);
			Machine.feederband.stop();
			RegulatedMotor m11 = new EV3MediumRegulatedMotor(MotorPort.B);
			m11.setSpeed(75);
			m11.rotate(120);
			m11.close();
			Delay.msDelay(300);
			Machine.feederband.setSpeed(40);
			Machine.feederband.backward();
		}
		else if (farbeid == 4) {
			RegulatedMotor m1 = new EV3MediumRegulatedMotor(MotorPort.B);
			m1.setSpeed(400);
			m1.rotate(-60);
			m1.close();
			Machine.feederband.setSpeed(200);
			Delay.msDelay(2600);
			Machine.feederband.stop();
			RegulatedMotor m11 = new EV3MediumRegulatedMotor(MotorPort.B);
			m11.setSpeed(75);
			m11.rotate(-120);
			m11.close();
			Delay.msDelay(300);
			Machine.feederband.setSpeed(40);
			Machine.feederband.backward();
		}	
	}
	
	//Methode �berpr�ft ob das aktuelle Sample auf einen Stein hinweist oder nicht
	//Je nachdem wird ein wahr/falsch zur�ckgegeben.
	public static boolean checkForBrick(float[] colorSample) {
		if (colorSample[0] >= 0.01 && colorSample[1] >= 0.015 && colorSample[1] >= 0.006) {
			return true;
		}
		else {
			return false;
		}		
	}
	
	//Gibt aus einem Sample die gerundeten EV3-RGB-Werte zur�ck. 
	public static String printRGB(float[] colorSample) {
		return "R: "+(Math.round(1000.0 * colorSample[0]) / 1000.0)+"R: "+(Math.round(1000.0 * colorSample[1]) / 1000.0)+"B: "+(Math.round(1000.0 * colorSample[2]) / 1000.0);
	}
	
	//Gibt aus einem Sample die gerundeten 255-RGB-Werte mit Kommata zur�ck. 
	public static String giveRGBString(float[] colorSample) {
		int[] colorSampleInt = new int[colorSample.length];
		for (int i=0; i < colorSample.length; i++) {
			//Umrechnen auf die offizielle RGB-Farbspanne mit Werten von 0 - 255
			colorSample[i] = colorSample[i]*1000;
			colorSampleInt[i] = (int) Math.round(colorSample[i]);
		}
		return (colorSampleInt[0]+", "+colorSampleInt[1]+", "+colorSampleInt[2]);
	}
	
	public static boolean checkForDrop(int colorid) {
		boolean drop = false;
		
		if (colorid == 1 && Machine.dropyellow == true) {
			drop = true;
		}
		if (colorid == 2 && Machine.dropred == true) {
			drop = true;
		}
		if (colorid == 3 && Machine.dropblue == true) {
			drop = true;
		}
		if (colorid == 4 && Machine.dropgreen == true) {
			drop = true;
		}
		return drop; 
	}
	
	//Wandelt die ColorID in den Farbnamen um, welcher als String zur�ckgegeben wird.
	public static String getColorName(int colorid) {
			String ret = "";
			if (colorid == 1) {ret = "yellow";}
			else if (colorid == 2) {ret = "red";}
			else if (colorid == 3) {ret = "blue";}
			else if (colorid == 4) {ret = "green";}
			else { ret = "Unbekannt";}
			return ret;
		}	
}
