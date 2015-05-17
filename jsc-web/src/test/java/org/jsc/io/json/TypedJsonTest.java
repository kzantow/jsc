package org.jsc.io.json;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.jsc.io.json.JsonService;

public class TypedJsonTest {
	static boolean printFields = true;
	
	public static class BaseClass {
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			Class<?> cls = getClass();
			try {
				if(!printFields) {
					java.lang.reflect.Field f = BaseClass.class.getDeclaredField("list");
					f.setAccessible(true);
					List<?> l = (List<?>)f.get(this);
					if(l.isEmpty()) {
						sb.append(cls.getSimpleName());
					} else {
						sb.append(cls.getSimpleName()).append(l.toString());
					}
				} else {
					while(cls != Object.class) {
						for(java.lang.reflect.Field f : cls.getDeclaredFields()) {
							f.setAccessible(true);
							if(sb.length() == 0) {
								sb.append(cls.getSimpleName()).append(" {");
							} else {
								sb.append(",");
							}
							sb.append(f.getName()).append("=");
							Object val = f.get(this);
							if(val == null) {
								sb.append("null");
							} else {
								sb.append(val.toString());
							}
						}
						cls = cls.getSuperclass();
					}
					
					sb.append("}");
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			return sb.toString();
		}

		public ArrayList<BaseClass> list = new ArrayList<BaseClass>();

		public int x;

	}

	public static class ExtendedClass2 extends BaseClass {
		public Long total;
	}

	public static class ExtendedClass1 extends BaseClass {
		public Long total;
		public Long number;
	}
	
	public static class SubSub extends ExtendedClass2 {
		double val = Math.random();
	}
	
	public static BaseClass createRandomObject(int childcount, int depth) {
		BaseClass obj;
		switch((int)(Math.random()*4)) {
			case 0: {
				BaseClass o = new BaseClass();
				obj = o;
				break;
			}
			case 1: {
				ExtendedClass1 o = new ExtendedClass1();
				o.total = (long)(Math.random()*Long.MAX_VALUE);
				obj = o;
				break;
			}
			case 2: {
				ExtendedClass2 o = new ExtendedClass2();
				obj = o;
				break;
			}
			default: {
				SubSub o = new SubSub();
				obj = o;
				break;
			}
		}
		if(depth > 0) {
			// randomly add children
			
			// Populate the sub-list
			for(int j = 0; j < childcount; j++) {
				BaseClass sub = createRandomObject(childcount, depth-1);
				obj.x = j;
				obj.list.add(sub);
			}
		}
		return obj;
	}
	
	private static long count(List<BaseClass> c) {
		long l = 0;
		for(BaseClass child : c) {
			l += 1 + count(child.list);
		}
		return l;
	}

	public static void main(String[] args) {

		BaseClass c1 = new BaseClass();
		ExtendedClass1 e1 = new ExtendedClass1();
		e1.total = 100L;
		e1.number = 5L;
		ExtendedClass2 e2 = new ExtendedClass2();
		e2.total = 200L;
		e2.x = 5;
		BaseClass c2 = new BaseClass();
		
		e2.list.add(c2);
		e2.list.add(e1);
		
		e1.list.add(new BaseClass());
		e1.list.add(new SubSub());
		e1.list.add(new SubSub());
		e1.list.add(new SubSub());

		c1.list.add(e1);
		c1.list.add(e2);
		c1.list.add(c2);

		// this is the instance of BaseClass before serialization
		System.out.println(c1);
		
		JsonService jsonifier = new JsonService();

		String json = jsonifier.toJson(c1);
		// this is the corresponding json
		System.out.println(json);

		Object newC1 = jsonifier.fromJson(BaseClass.class, json);

		System.out.println(newC1);
		
		System.out.println();
		
		List<BaseClass> al = new ArrayList<BaseClass>();
		al.add(c1);
		
		json = jsonifier.toJson(al);
		// this is the corresponding json
		System.out.println(json);

		newC1 = jsonifier.fromJson(List.class, json);

		System.out.println(newC1);
		
		// speed test
		al.clear();
		for(int i = 0; i < 2; i++) {
			BaseClass obj = createRandomObject(2, 3);
			obj.x = i;
			al.add(obj);
		}
		
		System.out.println();
		System.out.println(al.toString());
		
		long start = System.currentTimeMillis();
		
		json = jsonifier.toJson(al);
		System.out.println(json);
		
		newC1 = jsonifier.fromJson(List.class, json);

		System.out.println(newC1);
		
		for(int depth = 0; depth <= 2; depth += 1) {
			for(int size = 100; size <= 1000; size += 100) {
				for(int childCount = 0; childCount <= 100; childCount += 10) {
					al.clear();
					for(int i = 0; i < size; i++) {
						BaseClass obj = createRandomObject(childCount, depth);
						obj.x = i;
						al.add(obj);
					}
					
					System.out.println();
					//System.out.println(al.toString());
					
					StringWriter w = new StringWriter();
					
					start = System.currentTimeMillis();
					jsonifier.toJson(al, w);
					System.out.println("TOOK: " + (System.currentTimeMillis()-start) + "ms for write at: " + size + ", " + childCount + ", " + depth + " (" + count(al));
					
					StringReader reader = new StringReader(w.toString());
					
					start = System.currentTimeMillis();
					newC1 = jsonifier.fromJson(List.class, reader);
					System.out.println("TOOK: " + (System.currentTimeMillis()-start) + "ms for read at: " + size+ ", " + childCount + ", " + depth);
					
					//System.out.println(newC1);
					if(!al.toString().equals(newC1.toString())) {
						throw new RuntimeException("Not deserialized properly!");
					}
				}
			}
		}
	}
}
