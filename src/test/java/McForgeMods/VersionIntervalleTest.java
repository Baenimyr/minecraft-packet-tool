package McForgeMods;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersionIntervalleTest {
	
	@Test
	public void comparaison() {
		VersionIntervalle dep1 = new VersionIntervalle(new Version(1, 0, 0), new Version(2, 0, 0), true, false);
		
		assertTrue(dep1.correspond(new Version(1, 0, 0)));
		assertTrue(dep1.correspond(new Version(1, 584, 0)));
		assertTrue(dep1.correspond(new Version(1, 0, 127)));
		assertFalse(dep1.correspond(new Version(2, 0, 0)));
		assertFalse(dep1.correspond(new Version(2, 0, 14)));
		assertFalse(dep1.correspond(new Version(0, 0, 988)));
		
		assertTrue(dep1.englobe(new VersionIntervalle(new Version(1, 0, 0))));
		assertTrue(dep1.englobe(dep1));
		assertFalse(dep1.englobe(new VersionIntervalle(new Version(1, 0, 0), new Version(3, 5, 0))));
	}
	
	@Test
	public void lecture() {
		VersionIntervalle dep1 = VersionIntervalle.read("(,1.0]");
		assertTrue(dep1.inclut_max);
		assertFalse(dep1.inclut_min);
		assertNull(dep1.minimum);
		assertEquals(new Version(1, 0, 0), dep1.maximum);
		
		VersionIntervalle dep2 = VersionIntervalle.read("(,1.0)");
		assertFalse(dep2.inclut_max);
		assertFalse(dep2.inclut_min);
		assertNull(dep2.minimum);
		assertEquals(new Version(1, 0, 0), dep2.maximum);
		
		VersionIntervalle dep3 = VersionIntervalle.read("[1.0,)");
		assertFalse(dep3.inclut_max);
		assertTrue(dep3.inclut_min);
		assertNull(dep3.maximum);
		assertEquals(new Version(1, 0, 0), dep3.minimum);
		
		VersionIntervalle dep4 = VersionIntervalle.read("[1.0]");
		assertTrue(dep4.inclut_max);
		assertTrue(dep4.inclut_min);
		assertEquals(new Version(1, 0, 0), dep4.minimum);
		assertEquals(dep4.minimum, dep4.maximum);
	}
	
	@Test
	public void intervalleAuto() {
		VersionIntervalle dep5 = VersionIntervalle.read("1.0");
		assertFalse(dep5.inclut_max);
		assertTrue(dep5.inclut_min);
		assertNotNull(dep5.minimum);
		assertNotNull(dep5.maximum);
		assertEquals(new Version(1, 0, 0), dep5.minimum);
		assertEquals(new Version(1, 1, 0), dep5.maximum);
		
		VersionIntervalle dep6 = VersionIntervalle.read("1.0.0");
		assertFalse(dep6.inclut_max);
		assertTrue(dep6.inclut_min);
		assertNotNull(dep6.minimum);
		assertNotNull(dep6.maximum);
		assertEquals(new Version(1, 0, 0), dep6.minimum);
		assertEquals(new Version(1, 0, 1), dep6.maximum);
	}
	
	@Test
	public void dependances() {
		Map<String, VersionIntervalle> intervalles = VersionIntervalle.lectureDependances(
				Collections.singletonList("mod@[2.54.3," + "2.55)"));
		assertEquals(1, intervalles.size());
		assertTrue(intervalles.get("mod").inclut_min);
		assertFalse(intervalles.get("mod").inclut_max);
		assertEquals(new Version(2,54,3), intervalles.get("mod").minimum);
		assertEquals(new Version(2,55,0), intervalles.get("mod").maximum);
	}
}