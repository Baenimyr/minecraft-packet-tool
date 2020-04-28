package McForgeMods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {
	
	@Test
	void lectureSimple() {
		Version version = Version.read("1.13.45.8865");
		assertEquals(1, version.get(0));
		assertEquals(13, version.get(1));
		assertEquals(45, version.get(2));
		assertEquals(8865, version.get(3));
		
		version = Version.read("0.9.54");
		assertEquals(0, version.get(0));
		assertEquals(9, version.get(1));
		assertEquals(54, version.get(2));
		assertEquals(0, version.get(3));
	}
	
	@Test
	void lectureRelease() {
		Version v1 = Version.read("0.12-87e");
		assertEquals(0, v1.get(0));
		assertEquals(12, v1.get(1));
		assertEquals(0, v1.get(2));
		assertNotNull(v1.getRelease());
		assertEquals("87e", v1.getRelease());
		assertNull(v1.build);
	}
	
	@Test
	void lectureBuild() {
		Version v2 = Version.read("1.12.2-alpha+484");
		assertEquals(1, v2.get(0));
		assertEquals(12, v2.get(1));
		assertEquals(2, v2.get(2));
		assertNotNull(v2.getRelease());
		assertEquals("alpha", v2.getRelease());
		assertNotNull(v2.getBuild());
		assertEquals("484", v2.getBuild());
	}
}