package Cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small deterministic numeric expression language used by cannon programs. */
public final class CannonExpression {
	public interface Values {
		double value(String name);
		double random();
		/** Visual field evaluators expose one normalized shared texture clock. */
		default double texturePhase() { return Double.NaN; }
		/** Resolve an authored named expression in a sampled coordinate scope. */
		default double valueInScope(String name,Values scope) { return value(name); }
	}

	private record PlaneValues(Values parent,double u,double v,double time,double textureTime) implements Values {
		public double value(String name){return switch(name){
			case "u"->u;case "v"->v;case "r"->Math.hypot(u,v);case "theta"->Math.atan2(v,u);case "t"->time;
			case "cycle.x","phase.cos"->Math.cos(time*Math.PI*2);case "cycle.y","phase.sin"->Math.sin(time*Math.PI*2);
			default->parent.valueInScope(name,this);
		};}
		public double valueInScope(String name,Values scope){return parent.valueInScope(name,scope);}
		public double random(){return parent.random();}
		public double texturePhase(){return textureTime;}
	}

	private interface Node { double eval(Values values); }
	private final Node root;
	private final String source;

	private CannonExpression(String source, Node root) {
		this.source = source;
		this.root = root;
	}

	public static CannonExpression constant(double value) {
		return new CannonExpression(Double.toString(value), v -> value);
	}

	public static CannonExpression parse(String source) {
		Parser parser = new Parser(source == null ? "" : source.trim());
		Node root = parser.expression();
		parser.require(TokenType.END);
		return new CannonExpression(source, root);
	}

	public double eval(Values values) {
		double result = root.eval(values);
		return Double.isFinite(result) ? result : 0.0;
	}

	@Override public String toString() { return source; }

	/** Compare accepted token spellings without joining separate names or operators. */
	static String spelling(String source) {
		StringBuilder result=new StringBuilder();
		for(Token token:new Parser(source).tokens)result.append(token.type).append(':').append(token.text.toLowerCase(Locale.ROOT)).append(';');
		return result.toString();
	}

	private enum TokenType { NUMBER, NAME, OP, LEFT, RIGHT, COMMA, END }
	private static final class Token {
		final TokenType type;
		final String text;
		Token(TokenType type, String text) { this.type = type; this.text = text; }
	}

	private static final class Parser {
		private final List<Token> tokens = new ArrayList<>();
		private int at;

		Parser(String source) {
			lex(source);
			tokens.add(new Token(TokenType.END, ""));
		}

		/** Conditional selection has the lowest precedence and associates to the right. */
		Node expression() {
			Node condition=or();
			if(!match("?"))return condition;
			Node yes=expression();
			if(!match(":"))throw error("expected ':' in conditional expression");
			Node no=expression();
			return v->truth(condition.eval(v))?yes.eval(v):no.eval(v);
		}

		private Node or() {
			Node left = and();
			while (match("||")) { Node a = left, b = and(); left = v -> truth(a.eval(v)) || truth(b.eval(v)) ? 1 : 0; }
			return left;
		}

		private Node and() {
			Node left = equality();
			while (match("&&")) { Node a = left, b = equality(); left = v -> truth(a.eval(v)) && truth(b.eval(v)) ? 1 : 0; }
			return left;
		}

		private Node equality() {
			Node left = comparison();
			while (peek("==") || peek("!=")) {
				String op = take().text; Node a = left, b = comparison();
				left = v -> (op.equals("==") == (Math.abs(a.eval(v) - b.eval(v)) <= 1e-9)) ? 1 : 0;
			}
			return left;
		}

		private Node comparison() {
			Node left = term();
			while (peek("<") || peek("<=") || peek(">") || peek(">=")) {
				String op = take().text; Node a = left, b = term();
				left = v -> compare(op, a.eval(v), b.eval(v)) ? 1 : 0;
			}
			return left;
		}

		private Node term() {
			Node left = factor();
			while (peek("+") || peek("-")) {
				String op = take().text; Node a = left, b = factor();
				left = op.equals("+") ? v -> a.eval(v) + b.eval(v) : v -> a.eval(v) - b.eval(v);
			}
			return left;
		}

		private Node factor() {
			Node left = power();
			while (peek("*") || peek("/") || peek("%")) {
				String op = take().text; Node a = left, b = power();
				if (op.equals("*")) left = v -> a.eval(v) * b.eval(v);
				else if (op.equals("/")) left = v -> safeDivide(a.eval(v), b.eval(v));
				else left = v -> safeModulo(a.eval(v), b.eval(v));
			}
			return left;
		}

		private Node power() {
			Node left = unary();
			if (match("^")) { Node a = left, b = power(); return v -> Math.pow(a.eval(v), b.eval(v)); }
			return left;
		}

		private Node unary() {
			if (match("-")) { Node n = unary(); return v -> -n.eval(v); }
			if (match("+")) return unary();
			if (match("!")) { Node n = unary(); return v -> truth(n.eval(v)) ? 0 : 1; }
			return primary();
		}

		private Node primary() {
			Token token = take();
			if (token.type == TokenType.NUMBER) {
				double value = Double.parseDouble(token.text);
				return v -> value;
			}
			if (token.type == TokenType.LEFT) {
				Node node = expression(); require(TokenType.RIGHT); return node;
			}
			if (token.type != TokenType.NAME) throw error("expected number, variable, or function");
			String name = token.text.toLowerCase(Locale.ROOT);
			if(name.startsWith("math.")&&(current().type==TokenType.LEFT||java.util.Set.of("math.pi","math.tau","math.e").contains(name)))name=name.substring(5);
			if (name.equals("true")) return v -> 1;
			if (name.equals("false")) return v -> 0;
			if (name.equals("pi")) return v -> Math.PI;
			if (name.equals("tau")) return v -> Math.PI * 2.0;
			if (name.equals("e")) return v -> Math.E;
			if (current().type != TokenType.LEFT) {String variable=name;return v -> v.value(variable);}
			take();
			ArrayList<Node> args = new ArrayList<>();
			if (current().type != TokenType.RIGHT) {
				do {
					// Vector spelling is shorthand for scalar coordinate arguments, not a new runtime type.
					if(java.util.Set.of("length","hypot","magnitude","norm","distance","dot","cross","segment_distance","rotate_x","rotate_y").contains(name)
							&&current().type==TokenType.NAME&&current().text.equalsIgnoreCase("vec2")
							&&at+1<tokens.size()&&tokens.get(at+1).type==TokenType.LEFT) {
						take();take();args.add(expression());require(TokenType.COMMA);args.add(expression());require(TokenType.RIGHT);
					} else args.add(expression());
				} while (takeIf(TokenType.COMMA));
			}
			require(TokenType.RIGHT);
			return function(name, args);
		}

		private Node function(String name, List<Node> args) {
			if(name.equals("if")&&args.size()==3)
				return v->truth(args.get(0).eval(v))?args.get(1).eval(v):args.get(2).eval(v);
			name=switch(name){
				case "arcsin"->"asin";case "arccos"->"acos";case "arctan"->"atan";case "arctan2"->"atan2";
				case "radians","to_radians"->"rad";case "degrees","to_degrees"->"deg";
				case "ln"->"log";case "ceiling"->"ceil";case "signum"->"sign";
				case "magnitude","norm"->"length";case "clamp01"->"saturate";
				case "smooth_step"->"smoothstep";case "smoother_step"->"smootherstep";
				case "interpolate"->"lerp";default->name;
			};
			if(name.equals("mod")&&args.size()==2)return v->safeModulo(args.get(0).eval(v),args.get(1).eval(v));
			if(name.equals("sample2d")&&(args.size()==3||args.size()==4))return values->{
				double u=args.get(1).eval(values),v=args.get(2).eval(values),time=args.size()==4?args.get(3).eval(values):values.value("t");
				return args.get(0).eval(new PlaneValues(values,u,v,time,args.size()==4?time:values.texturePhase()));
			};
			if (name.equals("rand") && args.isEmpty()) return Values::random;
			if (name.equals("rand") && args.size() == 2) return v -> {
				double a = args.get(0).eval(v), b = args.get(1).eval(v);
				return a + (b - a) * v.random();
			};
			if (name.equals("abs") && args.size() == 1) return v -> Math.abs(args.get(0).eval(v));
			if (name.equals("sqrt") && args.size() == 1) return v -> Math.sqrt(Math.max(0, args.get(0).eval(v)));
			if (name.equals("sin") && args.size() == 1) return v -> Math.sin(args.get(0).eval(v));
			if (name.equals("cos") && args.size() == 1) return v -> Math.cos(args.get(0).eval(v));
			if (name.equals("tan") && args.size() == 1) return v -> Math.tan(args.get(0).eval(v));
			if (name.equals("asin") && args.size() == 1) return v -> Math.asin(clamp(args.get(0).eval(v), -1, 1));
			if (name.equals("acos") && args.size() == 1) return v -> Math.acos(clamp(args.get(0).eval(v), -1, 1));
			if (name.equals("atan") && args.size() == 1) return v -> Math.atan(args.get(0).eval(v));
			if (name.equals("atan2") && args.size() == 2) return v -> Math.atan2(args.get(0).eval(v), args.get(1).eval(v));
			if (name.equals("floor") && args.size() == 1) return v -> Math.floor(args.get(0).eval(v));
			if (name.equals("ceil") && args.size() == 1) return v -> Math.ceil(args.get(0).eval(v));
			if (name.equals("round") && args.size() == 1) return v -> Math.rint(args.get(0).eval(v));
			if (name.equals("sign") && args.size() == 1) return v -> Math.signum(args.get(0).eval(v));
			if(name.equals("exp")&&args.size()==1)return v->Math.exp(Math.max(-64,Math.min(64,args.get(0).eval(v))));
			if(name.equals("log")&&args.size()==1)return v->Math.log(Math.max(1e-12,args.get(0).eval(v)));
			if (name.equals("fract") && args.size() == 1) return v -> {
				double x = args.get(0).eval(v); return x - Math.floor(x);
			};
			if (name.equals("rad") && args.size() == 1) return v -> Math.toRadians(args.get(0).eval(v));
			if (name.equals("deg") && args.size() == 1) return v -> Math.toDegrees(args.get(0).eval(v));
			if (name.equals("min") && args.size() >= 2) return v -> {double n=args.get(0).eval(v);for(int i=1;i<args.size();i++)n=Math.min(n,args.get(i).eval(v));return n;};
			if (name.equals("max") && args.size() >= 2) return v -> {double n=args.get(0).eval(v);for(int i=1;i<args.size();i++)n=Math.max(n,args.get(i).eval(v));return n;};
			if (name.equals("pow") && args.size() == 2) return v -> Math.pow(args.get(0).eval(v), args.get(1).eval(v));
			if (name.equals("distance") && args.size() == 4) return v -> Math.hypot(
					args.get(2).eval(v) - args.get(0).eval(v), args.get(3).eval(v) - args.get(1).eval(v));
			if ((name.equals("length") || name.equals("hypot")) && args.size() == 2)
				return v -> Math.hypot(args.get(0).eval(v), args.get(1).eval(v));
			if ((name.equals("length") || name.equals("hypot")) && args.size() == 3)
				return v -> Math.sqrt(square(args.get(0).eval(v)) + square(args.get(1).eval(v)) + square(args.get(2).eval(v)));
			if (name.equals("distance3") && args.size() == 6) return v -> Math.sqrt(
					square(args.get(3).eval(v) - args.get(0).eval(v))
					+ square(args.get(4).eval(v) - args.get(1).eval(v))
					+ square(args.get(5).eval(v) - args.get(2).eval(v)));
			if(name.equals("dot")&&args.size()==4)return v->args.get(0).eval(v)*args.get(2).eval(v)+args.get(1).eval(v)*args.get(3).eval(v);
			if(name.equals("cross")&&args.size()==4)return v->args.get(0).eval(v)*args.get(3).eval(v)-args.get(1).eval(v)*args.get(2).eval(v);
			if(name.equals("angle_delta")&&args.size()==2)return v->Math.atan2(Math.sin(args.get(1).eval(v)-args.get(0).eval(v)),Math.cos(args.get(1).eval(v)-args.get(0).eval(v)));
			if(name.equals("segment_distance")&&args.size()==6)return v->segmentDistance(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v),args.get(3).eval(v),args.get(4).eval(v),args.get(5).eval(v));
			if(name.equals("smoothmin")&&args.size()==3)return v->smoothMin(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v));
			if(name.equals("smoothmax")&&args.size()==3)return v->-smoothMin(-args.get(0).eval(v),-args.get(1).eval(v),args.get(2).eval(v));
			if(name.equals("sd_circle")&&args.size()==5)return v->Math.hypot(args.get(0).eval(v)-args.get(2).eval(v),args.get(1).eval(v)-args.get(3).eval(v))-Math.abs(args.get(4).eval(v));
			if(name.equals("sd_ellipse")&&args.size()==6)return v->ellipseDistance(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v),args.get(3).eval(v),args.get(4).eval(v),args.get(5).eval(v));
			if(name.equals("sd_box")&&args.size()==7)return v->boxDistance(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v),args.get(3).eval(v),args.get(4).eval(v),args.get(5).eval(v),args.get(6).eval(v));
			if(name.equals("sd_segment")&&args.size()==7)return v->segmentDistance(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v),args.get(3).eval(v),args.get(4).eval(v),args.get(5).eval(v))-Math.abs(args.get(6).eval(v));
			if(name.equals("sd_arc")&&args.size()==8)return v->arcDistance(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v),args.get(3).eval(v),args.get(4).eval(v),args.get(5).eval(v),args.get(6).eval(v),args.get(7).eval(v));
			if(name.equals("rotate_x")&&args.size()==3)return v->args.get(0).eval(v)*Math.cos(args.get(2).eval(v))-args.get(1).eval(v)*Math.sin(args.get(2).eval(v));
			if(name.equals("rotate_y")&&args.size()==3)return v->args.get(0).eval(v)*Math.sin(args.get(2).eval(v))+args.get(1).eval(v)*Math.cos(args.get(2).eval(v));
			if (name.equals("hash") && args.size() >= 1 && args.size() <= 4) return v -> {
				double sum = 0;
				for (int i = 0; i < args.size(); i++) sum += args.get(i).eval(v) * (12.9898 + i * 45.164);
				double raw = Math.sin(sum) * 43758.5453123;
				return raw - Math.floor(raw);
			};
			if(name.equals("noise")&&args.size()>=2&&args.size()<=4)return v->{double x=args.get(0).eval(v),y=args.get(1).eval(v),z=args.size()>2?args.get(2).eval(v):0,seed=args.size()>3?args.get(3).eval(v):0,phase=v.texturePhase();if(Double.isFinite(phase)){double turn=phase*Math.PI*2;x+=Math.cos(turn)*.37;y+=Math.sin(turn)*.37;z=0;}return valueNoise(x,y,z,seed);};
			if(name.equals("fbm")&&args.size()>=2&&args.size()<=4)return v->{double x=args.get(0).eval(v),y=args.get(1).eval(v),z=args.size()>2?args.get(2).eval(v):0,seed=args.size()>3?args.get(3).eval(v):0,phase=v.texturePhase();if(Double.isFinite(phase)){double turn=phase*Math.PI*2;x+=Math.cos(turn)*.37;y+=Math.sin(turn)*.37;z=0;}return fractalNoise(x,y,z,seed,false);};
			if(name.equals("ridged")&&args.size()>=2&&args.size()<=4)return v->{double x=args.get(0).eval(v),y=args.get(1).eval(v),z=args.size()>2?args.get(2).eval(v):0,seed=args.size()>3?args.get(3).eval(v):0,phase=v.texturePhase();if(Double.isFinite(phase)){double turn=phase*Math.PI*2;x+=Math.cos(turn)*.37;y+=Math.sin(turn)*.37;z=0;}return fractalNoise(x,y,z,seed,true);};
			if(name.equals("voronoi")&&args.size()>=2&&args.size()<=4)return v->{double x=args.get(0).eval(v),y=args.get(1).eval(v),z=args.size()>2?args.get(2).eval(v):0,seed=args.size()>3?args.get(3).eval(v):0,phase=v.texturePhase();if(Double.isFinite(phase)){double turn=phase*Math.PI*2;x+=Math.cos(turn)*.31;y+=Math.sin(turn)*.31;z=0;}return voronoi(x,y,z,seed);};
			if (name.equals("lerp") && args.size() == 3) return v -> {
				double t = clamp(args.get(2).eval(v), 0, 1);
				return args.get(0).eval(v) + (args.get(1).eval(v) - args.get(0).eval(v)) * t;
			};
			if(name.equals("inverse_lerp")&&args.size()==3)return v->{double a=args.get(0).eval(v),b=args.get(1).eval(v);return Math.abs(b-a)<=1e-12?0:clamp((args.get(2).eval(v)-a)/(b-a),0,1);};
			if(name.equals("remap")&&args.size()==5)return v->{double a=args.get(0).eval(v),b=args.get(1).eval(v),c=args.get(2).eval(v),d=args.get(3).eval(v);double t=Math.abs(b-a)<=1e-12?0:(args.get(4).eval(v)-a)/(b-a);return c+(d-c)*t;};
			if (name.equals("clamp") && args.size() == 3) return v -> clamp(args.get(0).eval(v), args.get(1).eval(v), args.get(2).eval(v));
			if(name.equals("saturate")&&args.size()==1)return v->clamp(args.get(0).eval(v),0,1);
			if(name.equals("step")&&args.size()==2)return v->args.get(1).eval(v)>=args.get(0).eval(v)?1:0;
			if(name.equals("wrap")&&args.size()==3)return v->wrap(args.get(0).eval(v),args.get(1).eval(v),args.get(2).eval(v));
			if(name.equals("pingpong")&&args.size()==2)return v->{double length=Math.abs(args.get(1).eval(v));if(length<=1e-12)return 0;double value=wrap(args.get(0).eval(v),0,length*2);return length-Math.abs(value-length);};
			if (name.equals("smoothstep") && args.size() == 3) return v -> {
				double lo = args.get(0).eval(v), hi = args.get(1).eval(v), x = args.get(2).eval(v);
				double t = Math.abs(hi - lo) <= 1e-12 ? 0 : clamp((x - lo) / (hi - lo), 0, 1);
				return t * t * (3.0 - 2.0 * t);
			};
			if(name.equals("smootherstep")&&args.size()==3)return v->{double lo=args.get(0).eval(v),hi=args.get(1).eval(v),x=args.get(2).eval(v);double t=Math.abs(hi-lo)<=1e-12?0:clamp((x-lo)/(hi-lo),0,1);return t*t*t*(t*(t*6-15)+10);};
			throw error("unknown function or wrong arity: " + name);
		}

		void require(TokenType type) {
			if (current().type != type) throw error("expected " + type + " but found '" + current().text + "'");
			take();
		}

		private boolean takeIf(TokenType type) { if (current().type != type) return false; take(); return true; }
		private boolean match(String op) { if (!peek(op)) return false; take(); return true; }
		private boolean peek(String op) { return current().type == TokenType.OP && current().text.equals(op); }
		private Token current() { return tokens.get(Math.min(at, tokens.size() - 1)); }
		private Token take() { return tokens.get(Math.min(at++, tokens.size() - 1)); }
		private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at token " + at); }

		private void lex(String source) {
			for (int i = 0; i < source.length();) {
				char c = source.charAt(i);
				if (Character.isWhitespace(c)) { i++; continue; }
				if (Character.isDigit(c) || c == '.') {
					int start = i++;
					while (i < source.length() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
					if (i < source.length() && (source.charAt(i) == 'e' || source.charAt(i) == 'E')) {
						i++; if (i < source.length() && (source.charAt(i) == '+' || source.charAt(i) == '-')) i++;
						while (i < source.length() && Character.isDigit(source.charAt(i))) i++;
					}
					tokens.add(new Token(TokenType.NUMBER, source.substring(start, i))); continue;
				}
				if (Character.isLetter(c) || c == '_') {
					int start = i++;
					while (i < source.length()) {
						char n = source.charAt(i);
						if (!Character.isLetterOrDigit(n) && n != '_' && n != '.') break;
						i++;
					}
					String word=source.substring(start,i),op=switch(word.toLowerCase(Locale.ROOT)){case "and"->"&&";case "or"->"||";case "not"->"!";default->null;};
					tokens.add(new Token(op==null?TokenType.NAME:TokenType.OP,op==null?word:op)); continue;
				}
				if (c == '(') { tokens.add(new Token(TokenType.LEFT, "(")); i++; continue; }
				if (c == ')') { tokens.add(new Token(TokenType.RIGHT, ")")); i++; continue; }
				if (c == ',') { tokens.add(new Token(TokenType.COMMA, ",")); i++; continue; }
				String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
				if(two.equals("**")||two.equals("<>")){tokens.add(new Token(TokenType.OP,two.equals("**")?"^":"!="));i+=2;continue;}
				if (two.equals("<=") || two.equals(">=") || two.equals("==") || two.equals("!=") || two.equals("&&") || two.equals("||")) {
					tokens.add(new Token(TokenType.OP, two)); i += 2; continue;
				}
				if ("+-*/%^<>!=?:".indexOf(c) >= 0) { tokens.add(new Token(TokenType.OP, c=='='?"==":Character.toString(c))); i++; continue; }
				throw new IllegalArgumentException("illegal expression character '" + c + "'");
			}
		}
	}

	private static boolean truth(double value) { return Math.abs(value) > 1e-9; }
	private static boolean compare(String op, double a, double b) {
		switch (op) { case "<": return a < b; case "<=": return a <= b; case ">": return a > b; default: return a >= b; }
	}
	private static double safeDivide(double a, double b) { return Math.abs(b) <= 1e-12 ? 0.0 : a / b; }
	private static double safeModulo(double a, double b) { return Math.abs(b) <= 1e-12 ? 0.0 : a % b; }
	private static double square(double value) { return value * value; }
	private static double segmentDistance(double px,double py,double ax,double ay,double bx,double by){double dx=bx-ax,dy=by-ay,length=dx*dx+dy*dy;if(length<=1e-12)return Math.hypot(px-ax,py-ay);double t=clamp(((px-ax)*dx+(py-ay)*dy)/length,0,1);return Math.hypot(px-(ax+dx*t),py-(ay+dy*t));}
	private static double arcDistance(double x,double y,double cx,double cy,double radius,double centerAngle,double halfSweep,double thickness){double dx=x-cx,dy=y-cy,r=Math.abs(radius),sweep=clamp(Math.abs(halfSweep),0,Math.PI),angle=Math.atan2(dy,dx),delta=Math.atan2(Math.sin(angle-centerAngle),Math.cos(angle-centerAngle));if(Math.abs(delta)<=sweep)return Math.abs(Math.hypot(dx,dy)-r)-Math.abs(thickness);double edge=centerAngle+Math.copySign(sweep,delta);return Math.hypot(dx-Math.cos(edge)*r,dy-Math.sin(edge)*r)-Math.abs(thickness);}
	private static double smoothMin(double a,double b,double amount){double k=Math.max(1e-9,Math.abs(amount)),h=clamp(.5+.5*(b-a)/k,0,1);return b+(a-b)*h-k*h*(1-h);}
	private static double ellipseDistance(double x,double y,double cx,double cy,double rx,double ry){double ax=Math.max(1e-6,Math.abs(rx)),ay=Math.max(1e-6,Math.abs(ry)),nx=(x-cx)/ax,ny=(y-cy)/ay;return(Math.hypot(nx,ny)-1)*Math.min(ax,ay);}
	private static double boxDistance(double x,double y,double cx,double cy,double hx,double hy,double rounding){double qx=Math.abs(x-cx)-Math.abs(hx),qy=Math.abs(y-cy)-Math.abs(hy),outside=Math.hypot(Math.max(qx,0),Math.max(qy,0)),inside=Math.min(Math.max(qx,qy),0);return outside+inside-Math.max(0,rounding);}
	private static double wrap(double value,double low,double high){double lo=Math.min(low,high),hi=Math.max(low,high),range=hi-lo;if(range<=1e-12)return lo;double out=(value-lo)%range;if(out<0)out+=range;return lo+out;}
	private static double valueNoise(double x,double y,double z,double seed){long x0=(long)Math.floor(x),y0=(long)Math.floor(y),z0=(long)Math.floor(z);double fx=smoother(x-x0),fy=smoother(y-y0),fz=smoother(z-z0);double a=lerp(hashLattice(x0,y0,z0,seed),hashLattice(x0+1,y0,z0,seed),fx),b=lerp(hashLattice(x0,y0+1,z0,seed),hashLattice(x0+1,y0+1,z0,seed),fx),c=lerp(hashLattice(x0,y0,z0+1,seed),hashLattice(x0+1,y0,z0+1,seed),fx),d=lerp(hashLattice(x0,y0+1,z0+1,seed),hashLattice(x0+1,y0+1,z0+1,seed),fx);return lerp(lerp(a,b,fy),lerp(c,d,fy),fz);}
	private static double fractalNoise(double x,double y,double z,double seed,boolean ridge){double sum=0,weight=.55,total=0;for(int octave=0;octave<5;octave++){double sample=valueNoise(x,y,z,seed+octave*19.17);if(ridge)sample=1-Math.abs(sample*2-1);sum+=sample*weight;total+=weight;x=x*2.03+17.13;y=y*2.01-11.71;z=z*1.97+3.19;weight*=.5;}return sum/total;}
	private static double voronoi(double x,double y,double z,double seed){long ix=(long)Math.floor(x),iy=(long)Math.floor(y);double closest=Double.POSITIVE_INFINITY;for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){long cx=ix+ox,cy=iy+oy;double px=cx+hashLattice(cx,cy,(long)Math.floor(z),seed),py=cy+hashLattice(cx,cy,(long)Math.floor(z)+31,seed+7.3);closest=Math.min(closest,Math.hypot(x-px,y-py));}return clamp(closest/1.4142135623730951,0,1);}
	private static double hashLattice(long x,long y,long z,double seed){long value=x*0x9e3779b97f4a7c15L+y*0xc2b2ae3d27d4eb4fL+z*0x165667b19e3779f9L+Double.doubleToLongBits(seed);value^=value>>>30;value*=0xbf58476d1ce4e5b9L;value^=value>>>27;value*=0x94d049bb133111ebL;value^=value>>>31;return(value>>>11)*0x1.0p-53;}
	private static double smoother(double x){return x*x*x*(x*(x*6-15)+10);}
	private static double lerp(double a,double b,double t){return a+(b-a)*t;}
	private static double clamp(double value, double lo, double hi) { return Math.max(Math.min(lo, hi), Math.min(Math.max(lo, hi), value)); }
}
