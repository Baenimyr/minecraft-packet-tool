package McForgeMods;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Une intervalle de versions englobe toutes les versions comprises entre les deux bornes.
 * <p>
 * Les bornes peuvent être inclusives ou exclusives. Si une version de borne est nulle, alors l'intervalle est ouverte
 * sur ce côté. Une intervalle doit respecter le format suivant:
 * <ul>
 *     <li>[v1,) pour v1 inclus sans limite supérieure</li>
 *     <li>(v1,v2): entre v1 et v2 exclus</li>
 *     <li>[,v2) = (,v2): inférieur à v2 exclus</li>
 *     <li>et les variantes d'inclusion/exclusion</li>
 *     <li>v1: voir {@link #read(String)}</li>
 * </ul>
 */
public class VersionIntervalle {
	public static final  Pattern ID                 = Pattern
			.compile("\\p{Alpha}\\p{Alnum}+(-\\p{Alnum}+)*", Pattern.CASE_INSENSITIVE);
	private static final String  VERSION            = "(\\d+)(\\.(\\d+)(\\.(\\d+)(\\.(\\d+))?)?)?(-(\\p{Alnum}+))?"
			+ "(\\+(\\p{Alnum}+))?";
	public static final  Pattern VERSION_INTERVALLE = Pattern.compile(
			"(?<bmin>[(\\[]?)(?<vmin>" + VERSION + ")?(?<inter>,\\s?(?<vmax>" + VERSION + ")?)?" + "(?<bmax>[)\\]]?)");
	
	final Version minimum, maximum;
	final boolean inclut_min, inclut_max;
	
	public VersionIntervalle() {
		this.minimum = this.maximum = null;
		this.inclut_min = this.inclut_max = false;
	}
	
	public VersionIntervalle(final VersionIntervalle v) {
		this.minimum = v.minimum;
		this.maximum = v.maximum;
		this.inclut_min = v.inclut_min;
		this.inclut_max = v.inclut_max;
	}
	
	public VersionIntervalle(Version minimum, Version maximum) {
		this(minimum, maximum, true, false);
	}
	
	public VersionIntervalle(Version minimum, Version maximum, boolean inclut_min, boolean inclut_max) {
		this.minimum = minimum;
		this.maximum = maximum;
		this.inclut_min = inclut_min;
		this.inclut_max = inclut_max;
	}
	
	public VersionIntervalle(Version v) {
		this.minimum = this.maximum = v;
		this.inclut_min = this.inclut_max = true;
	}
	
	public VersionIntervalle(Version version, int precision) {
		this(version, version.set(precision, version.get(precision) + 1));
	}
	
	/**
	 * Crée une dépendance n'acceptant qu'une seule version. Par exemple la présence d'un mod déjà installé, contraint
	 * la résolution de version à celle disponible.
	 */
	public VersionIntervalle(Version version, int precision, boolean inclut_min, boolean inclut_max) {
		this(version, version.set(precision, version.get(precision) + 1), inclut_min, inclut_max);
	}
	
	public static VersionIntervalle ouvert() {
		return new VersionIntervalle(null, null);
	}
	
	/**
	 * Lit une intervalle de version à partir d'une chaîne de caractère.
	 * <p>
	 * Le format des versions doit correspondre au format définit par {@link Version}. Si une seule version est fournie
	 * et qu'il n'y a pas de symboles d'intervalle ('(', ')', '[',']'), alors l'intervalle créée inclue la version
	 * fournie jusqu'à la version supérieure exclue. La version supérieure dépend du nombre d'éléments précisés pour la
	 * version, même nul. Ainsi
	 * <ul>
	 *     <li>1.2 -> [1.2,1.3)</li>
	 *     <li>1.2.0 -> [1.2,1.2.1)</li>
	 * </ul>
	 *
	 * @param in texte de l'interval
	 * @throws VersionIntervalleFormatException si le format n'est pas respecté.
	 */
	public static VersionIntervalle read(String in) throws VersionIntervalleFormatException {
		final Matcher m = VERSION_INTERVALLE.matcher(in);
		if (m.find()) {
			if (m.end() < in.length())
				throw new VersionIntervalleFormatException("Format invalide à " + m.end() + ": " + in);
			
			VersionIntervalle intervalle;
			final boolean inclut_min, inclut_max;
			final Version v_minimale, v_maximale;
			int precision = 0;
			
			inclut_min = !"(".equals(m.group("bmin"));
			inclut_max = "]".equals(m.group("bmax"));
			
			try {
				final String s_min = m.group("vmin");
				v_minimale = s_min == null ? null : Version.read(s_min);
				
				if (s_min != null) for (int i = 0; i < s_min.length(); i++) {
					if (s_min.charAt(i) == '.') precision++;
					else if (!Character.isDigit(s_min.charAt(i))) break;
				}
			} catch (IllegalArgumentException iae) {
				throw new VersionIntervalleFormatException(
						"La version minimale " + m.group("vmin") + " n'a pas un format valide !");
			}
			
			if (m.group("inter") != null) { // intervalle large
				try {
					final String s_max = m.group("vmax");
					v_maximale = s_max == null ? null : Version.read(s_max);
				} catch (IllegalArgumentException iae) {
					throw new VersionIntervalleFormatException(
							"La version maximale " + m.group("vmax") + " n'a pas un format valide !");
				}
				
				intervalle = new VersionIntervalle(v_minimale, v_maximale, inclut_min, inclut_max);
			} else {
				if (inclut_max) intervalle = new VersionIntervalle(v_minimale);
				else intervalle = new VersionIntervalle(v_minimale, precision, inclut_min, inclut_max);
			}
			
			return intervalle;
		} else
			throw new VersionIntervalleFormatException("Ce n'est pas un intervalle de version valide: \"" + in + "\"");
	}
	
	/**
	 * Décompose une chaine de caractère réprésentant un mod et une intervalle de version.
	 * <p>
	 * Si plusieurs même identifiant de mod apparaissent dans la liste, seule l'intersection des intervalles est
	 * conservée. Le texte doit être sous la forme <i>modid</i>[@<i>intervalle</i>]. L'intervalle est en option donc si
	 * aucune intervalle n'est précisée, l'intervalle est nulle.
	 * <p>
	 * Exemples:
	 * <ul>
	 *     <li>modid</li>
	 *     <li>modid@2.3</li>
	 *     <li>modid@[2.3,)</li>
	 *     <li>modid@(,4.6]</li>
	 * </ul>
	 *
	 * @param entree: une liste de mod
	 * @return une map {modid -> intervalle}
	 * @throws IllegalArgumentException si la syntaxe est incorrecte.
	 */
	public static Map<String, VersionIntervalle> lectureDependances(Iterable<?> entree)
			throws IllegalArgumentException {
		final Map<String, VersionIntervalle> resultat = new HashMap<>();
		for (Object o : entree) {
			final String texte = o.toString();
			int pos;
			
			Matcher match_id = ID.matcher(texte);
			if (!match_id.find()) {
				throw new VersionIntervalleFormatException("id incorrect: " + texte);
			}
			final String modid = match_id.group();
			pos = match_id.end();
			
			final VersionIntervalle versionIntervalle;
			if (pos == texte.length()) versionIntervalle = VersionIntervalle.ouvert();
			else if (texte.charAt(pos) == '@') {
				versionIntervalle = VersionIntervalle.read(texte.substring(pos + 1));
			} else {
				throw new VersionIntervalleFormatException("Intervalle illisible à " + pos + ": " + texte);
			}
			
			if (resultat.containsKey(modid) && resultat.get(modid) != null) {
				resultat.merge(modid, versionIntervalle, VersionIntervalle::intersection);
			} else resultat.put(modid, versionIntervalle);
		}
		return resultat;
	}
	
	public Version minimum() {
		return this.minimum;
	}
	
	public Version maximum() {
		return this.maximum;
	}
	
	/**
	 * Cette intervalle devient l'intersection des deux intervalles.
	 *
	 * @param d: une autre intervalle.
	 */
	public VersionIntervalle intersection(VersionIntervalle d) {
		if (Objects.isNull(d)) return this;
		boolean inclut_min = this.inclut_min, inclut_max = this.inclut_max;
		Version minimum = this.minimum, maximum = this.maximum;
		if (Objects.equals(this.minimum, d.minimum)) inclut_min = !(!this.inclut_min || !d.inclut_min);
		else if (this.minimum == null || (d.minimum != null && d.minimum.compareTo(this.minimum) > 0)) {
			minimum = d.minimum;
			inclut_min = d.inclut_min;
		}
		
		if (Objects.equals(this.maximum, d.maximum)) inclut_max = !(!this.inclut_max || !d.inclut_max);
		else if (this.maximum == null || (d.maximum != null && d.maximum.compareTo(this.maximum) < 0)) {
			maximum = d.maximum;
			inclut_max = d.inclut_max;
		}
		return new VersionIntervalle(minimum, maximum, inclut_min, inclut_max);
	}
	
	/**
	 * @return {@code true} si la version est comprise dans l'intervalle.
	 */
	public boolean contains(Version version) {
		return version != null && ((minimum == null || version.compareTo(minimum) >= (inclut_min ? 0 : 1)) && (
				maximum == null || version.compareTo(maximum) <= (inclut_max ? 0 : -1)));
	}
	
	@Deprecated
	public boolean correspond(Version version) {
		return this.contains(version);
	}
	
	public boolean englobe(VersionIntervalle intervalle) {
		return (this.contains(intervalle.minimum) || (!inclut_min && !intervalle.inclut_min && Objects
				.equals(minimum, intervalle.minimum))) && (this.contains(intervalle.maximum) || (!inclut_max
				&& !intervalle.inclut_max && Objects.equals(maximum, intervalle.maximum)));
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		VersionIntervalle that = (VersionIntervalle) o;
		return inclut_min == that.inclut_min && inclut_max == that.inclut_max && Objects.equals(minimum, that.minimum)
				&& Objects.equals(maximum, that.maximum);
	}
	
	public boolean isEmpty() {
		final int i = this.minimum.compareTo(this.maximum);
		if (i < 0) return false;
		if (i > 0) return true;
		return !(this.inclut_min && this.inclut_max);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(minimum, maximum, inclut_min, inclut_max);
	}
	
	@Override
	public String toString() {
		return (minimum != null ? (inclut_min ? '[' : '(') + minimum.toString() : '(') + "," + (maximum != null ?
				maximum.toString() + (inclut_max ? ']' : ')') : ')');
	}
	
	public String toStringMinimal() {
		return minimum != null ? this.minimum.toString(this.minimum.precision(), false, false) : this.toString();
	}
	
	public static class VersionIntervalleFormatException extends IllegalArgumentException {
		public VersionIntervalleFormatException(String texte) {
			super(texte);
		}
	}
}
