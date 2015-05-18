package ufinder;

public class WindowPair {
	
	private int ystart, xleft, xright, nx, ny;
	
	public WindowPair() {
		ystart=0; xleft=0; xright=0; nx=0; ny=0; 
		// TODO Auto-generated constructor stub
	}
	
	public WindowPair(int ystart_in, int xleft_in, int xright_in, int nx_in, int ny_in) {
		// constructor from integers
		ystart=ystart_in;
		xleft =xleft_in;
		xright=xright_in;
		nx    =nx_in;
		ny    =ny_in;		
	}
	
	public WindowPair(WindowPair in){
		// copy constructor
		this(in.ystart,in.xleft,in.xright,in.nx,in.ny);
	}
	
	public void set(int ystart_in, int xleft_in, int xright_in, int nx_in, int ny_in){
		this.ystart=ystart_in;
		this.xleft =xleft_in;
		this.xright=xright_in;
		this.nx    =nx_in;
		this.ny    =ny_in;	
	}

	public int get_ystart(){
		return this.ystart;
	}
	public int get_xleft(){
		return this.xleft;
	}
	public int get_xright(){
		return this.xright;
	}
	public int get_nx(){
		return this.nx;
	}
	public int get_ny(){
		return this.ny;
	}
	
}
