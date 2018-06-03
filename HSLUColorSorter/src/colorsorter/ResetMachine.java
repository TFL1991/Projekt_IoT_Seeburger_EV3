// Diese Klasse setzt den Roboter auf einen Urpsrungszustand zur�ck

package colorsorter;

import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;

public class ResetMachine {

	public static void reset() {
		// To be discussed: W�rde bei jedem Start den Schieber "blind"
		// nach vorne schieben, obwohl dieser evtl. schon vorne ist
		// Bei einem "Stromausfall" k�nnten aber auch die Abwurfmotoren
		// in der falschen Stellung sein, und diese k�nnte man dann nicht 
		// so einfach resetten bzw. gar nicht.
		// Da bei einem normalen Stop der Schieber eh immer geschlossen wird
		// halte ich die Funktion f�r nicht sinnvoll und "unsch�n".
		//Sortierer.slider = new EV3LargeRegulatedMotor(MotorPort.D);
		//Sortierer.slider.setSpeed(20);
		//Sortierer.slider.rotate(125);
		
		// Setzt Variablen der Maschine auf den Ursprungszustand
		Machine.machineoutofstone = false;
		Machine.timewithoutstoneonfeederband = 0.0;
		Machine.automaticoff = 20.0;
		Machine.dropred = Machine.dropyellow = 
		Machine.dropblue = Machine.dropgreen = false;
		Machine.stonescounted = 0;
		Machine.redstonescounted = 0;
		Machine.yellowstonescounted = 0;
		Machine.greenstonescounted = 0; 
		Machine.bluestonescounted = 0;
		
	}

}
