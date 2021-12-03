package  edu.rutgers.masondemo1;

import sim.engine.*;
import sim.field.continuous.*;
import sim.util.*;

public class Person implements Steppable{
    
    final boolean male;
    final String surname;
    final int birthYear;
    final int targetChildren;
    boolean dead=false;
    Person spouse=null;
    IterativeRepeat repeater;
    int children=0;
    
    Person(SimState state,  boolean _male, String _surname, int _birthYear ) {
	Population pop  = (Population) state;
	male= _male;
	surname =_surname;
	birthYear = _birthYear;
	if (male) 	targetChildren=0;
	else {
	    if (pop.tfr<1) targetChildren=pop.random.nextBoolean(pop.tfr)?1:0;
	    else {
		int q = (int)(pop.tfr);
		targetChildren=pop.random.nextInt(2*q+1) +
		    (pop.random.nextBoolean(pop.tfr-q)?1:0);
	    }
	}
		 
    }
    
    int getAge(int now) {
	return now - birthYear;
    }
    
    public void step(SimState state) {
	if (dead) throw new IllegalArgumentException("We should not step dead persons");
	Population pop  = (Population) state;
	//	Continuous2D yard = pop.yard;
	//Double2D me = pop.yard.getObjectLocation(this);
	int now = (int)pop.schedule.getTime();
	int age = getAge(now);

	if (!male) {
	    if (spouse==null) {
		if (age>=18 &&  pop.random.nextBoolean(0.20)) {
		    Person groom = pop.findRandomGroom(age);
		    if (groom!=null) {
			spouse=groom;
			groom.spouse=this;
		    }
		}
	    } else if (children<targetChildren && pop.random.nextBoolean(0.33)) {
		Person child = new Person(pop, pop.random.nextBoolean(), spouse.surname,now);
		pop.add(child);
		children++;
	    }
	}

	if (age>40) {
	    double deathRate = Math.min(0.1*(age-40)/40.0, 1.0);
	
	    if (pop.random.nextBoolean(deathRate)) {
		pop.remove(this);
	    }
	}
	
    }
}
