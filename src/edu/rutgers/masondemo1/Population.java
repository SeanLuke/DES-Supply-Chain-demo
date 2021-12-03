package  edu.rutgers.masondemo1;

import java.util.*;
import java.text.*;

import sim.engine.*;
import sim.util.*;
import sim.field.continuous.*;

/** The main class for a  simple demographics simulation */
public class Population extends SimState {


    static final String allSurnames[]={"A","B","C","D","E","F","G","H","I","J"};

    
   
    public Population(long seed)    {
	super(seed);
    }


    /** A CensusTaker is an agent that carries out population censuses */
    static class CensusTaker implements Steppable{
	static final int CENSUS_INTERVAL=30;
	/** Used to de-schedule the CensusTaker if there are no more persons
	    left to count, so that we can finish the run. */
	IterativeRepeat ir=null;

	/** Creates a CensusTaker and schedules it. This is supposed to
	    be run from Population.start(), when getTime()==-1. */
	CensusTaker(Schedule schedule) {
	    if (!schedule.scheduleOnceIn(1, this)) throw new IllegalArgumentException("Cannot schedule the CensusTaker");
	}


	
	public void step(SimState state) {
	    //if (((int)state.schedule.getTime())%25==0) {
	    int popCnt = ((Population)state).census();
	    if (popCnt==0) {
		if (ir!=null) ir.stop();
	    } else if (ir==null) {
		ir = state.schedule.scheduleRepeating(this, CENSUS_INTERVAL);
	    }
	}
    }
	
    int census() {
	int now = (int)schedule.getTime();
	TreeMap<String,Integer> surnameCnt=new TreeMap();
	int marriedCnt=0, maleCnt=0, femaleCnt=0, sumAge=0;
	for(Person p: allPersons) {
	    if (p.male) maleCnt++; else femaleCnt++;
	    sumAge+=p.getAge(now);
	    String key = p.surname;
	    Integer cnt = surnameCnt.get(key);
	    cnt =(cnt==null)? 1: cnt+1;
	    surnameCnt.put(key,cnt);
	    if (p.spouse!=null) marriedCnt++;
	}
	Vector<String>  v =new Vector<>();
	for(String key: allSurnames) {
	    Integer cnt = surnameCnt.get(key);
	    if (cnt==null) cnt=0;
	    v.add(key + ":"+cnt);
	}
	final DecimalFormat df = new DecimalFormat("##.#");
	int popCnt=allPersons.size();
	double marriedRate = (popCnt==0)? 0: (double)marriedCnt/popCnt;
	double avgAge = (popCnt==0)? 0: (double)sumAge/popCnt;
	System.out.println("Year "+now+
			   //", step="+schedule.getSteps()+
			   ", pop="+popCnt+" (M="+maleCnt+", F=" +femaleCnt+
			   ", <age>=" +df.format(avgAge)+", "+ df.format(marriedRate*100)+"% married). Surnames: "+
			   
			   String.join(" ", v));
	return popCnt;
    }

    HashSet<Person> allPersons = new HashSet<Person>();

    /*
    public void postSchedule(){
	super.postSchedule();
	if (((int)schedule.getTime())%20==0) census();
    }
    */

    void add(Person person) {
	allPersons.add(person);
	
	IterativeRepeat ir =	schedule.scheduleRepeating(person);
	person.repeater=ir;
    }

    void remove(Person person) {
	if (person.spouse!=null) {
	    person.spouse.spouse=null;
	    person.spouse=null;
	}
	allPersons.remove(person);
	person.repeater.stop();
	person.dead=true;
    }
    
    double tfr;
    
    public void start(){
	super.start();


	int initPop = Integer.parseInt(argv[0]);

	tfr = Double.parseDouble(argv[1]);
	System.out.println("Target TFR=" + 	tfr);
	System.out.println("Population.start(), getTime=" + schedule.getTime());
	
	// add some persons
	int cnt=0;
	for(int k=0; k<allSurnames.length;k++) {
	    int cnt2=((k+1)*initPop)/allSurnames.length;
	    for(;cnt<cnt2;cnt++) {
		Person person = new Person(this, cnt%2==0, allSurnames[k], 0);
		add(person);
	    }
	}
	CensusTaker ct = new CensusTaker(schedule);

    }

 /** Is ci[pos] different from all preceding array elements?
     */
    private static boolean isUnique(int[] ci, int pos) {
	for(int i=0; i<pos; i++) {
	    if (ci[i] == ci[pos]) return false;	    
	}
	return true;
    }
    
    
    /** Returns an array of nc distinct numbers randomly selected from
	the range [0..n), and randomly ordered. If nc==n, this is simply 
	a random permutation of [0..n).
	
	<p>The average cost is O( nc * n).
     */
    public int[] randomSample(int n, int nc) {
	if (nc > n) throw new IllegalArgumentException("Cannot select " + nc + " values out of " + n + "!");
	int ci[] = new int[nc]; 
	for(int i=0; i<ci.length; i++) {
	    do {
		ci[i] = random.nextInt(n);
	    } while(!isUnique(ci,i));		
	}
	return ci;
    }
    

    Person findRandomGroom(int age0) {
	Vector<Person> eligibles  = new Vector<>();
	int now = (int)schedule.getTime();
	for(Person p: allPersons) {
	    int age = p.getAge(now);
	    if (p.male && p.spouse==null && age>=20 && age>=age0-5 && age<=age0+10) eligibles.add(p);	    
	}
	int n =eligibles.size();
	return n==0? null: eligibles.get( random.nextInt(n));
    }

    double rawBirthRate;


    static  String[] argv;
    
    public static void main(String[] _argv){
	argv= _argv;

	doLoop(Population.class, argv);
	System.exit(0);
    }
}
