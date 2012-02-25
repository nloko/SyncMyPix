/**
 * JFlex specification for transcribing Greek characters into latin
 * according to ISO 843:1997.
 *
 *    GreekTranscribe.java is part of SyncMyPix
 *
 *    Author: Diomidis Spinellis <dds@aueb.gr>
 *
 *	  Copyright (c) 2012 Diomidis Spinellis
 *
 *    SyncMyPix is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    SyncMyPix is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with SyncMyPix.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Convert this file into Java using a sequence line the following
 * grconv -S UTF-8 -T Java greek-transcribe.lex >foo.lex
 * jflex foo.lex
 *
 * See also
 * http://www.spinellis.gr/sw/greek/grconv/
 * http://jflex.de/faq.html
 * http://transliteration.eki.ee/pdf/Greek.pdf
 */

package gr.spinellis.greek;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException ;

/**
 * Transcribe Greek input per ISO 843:1997 part 2
 */
%%

%class GreekTranscribe
%public
%unicode

%{
    StringBuffer string = new StringBuffer();

    private String stress_a;
    private String stress_A;
    private String stress_e;
    private String stress_E;
    private String stress_i;
    private String stress_I;
    private String stress_o;
    private String stress_O;
    private String stress_u;
    private String stress_U;
    private String stress_y;
    private String stress_Y;

    private String diair_i;
    private String diair_I;
    private String diair_y;
    private String diair_Y;

    /**
     * Set whether the result should include stressed characters.
     */
    private void outputStressed(boolean stressed) {
	if (stressed) {
	    stress_a = "á";
	    stress_A = "Á";
	    stress_e = "é";
	    stress_E = "É";
	    stress_i = "í";
	    stress_I = "Í";
	    stress_o = "ó";
	    stress_O = "Ó";
	    stress_u = "ú";
	    stress_U = "Ú";
	    stress_y = "ý";
	    stress_Y = "Ý";

	    diair_i = "ï";
	    diair_I = "Ï";
	    diair_y = "ÿ";
	    diair_Y = "Ÿ";
	} else {
	    stress_a = "a";
	    stress_A = "A";
	    stress_e = "e";
	    stress_E = "E";
	    stress_i = "i";
	    stress_I = "I";
	    stress_o = "o";
	    stress_O = "O";
	    stress_u = "u";
	    stress_U = "U";
	    stress_y = "y";
	    stress_Y = "Y";

	    diair_i = "i";
	    diair_I = "I";
	    diair_y = "y";
	    diair_Y = "Y";
	}
    }

    /**
     * Transcribe and print on the standard output the specified input file.
     */
    public static void main(String [] args) {
        if (args.length != 2) {
            System.err.println("Usage: CharCount file encoding");
            System.exit(1);
        }

        // Open file
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), args[1]));
        } catch (FileNotFoundException e) {
            System.err.println("Unable to open file " + args[0] + ": " + e.getMessage());
            System.exit(1);
        } catch (UnsupportedEncodingException e) {
            System.err.println("Unsupported encoding " + args[1] + ": " + e.getMessage());
        }

	GreekTranscribe gt = new GreekTranscribe(in);
	String s;
	try {
	    while ((s = gt.transcribe()) != null)
		System.out.print(s);
	    in.close();
	} catch (IOException e) {
            System.err.println("Input  output exception: " + e.getMessage());
        }
    }

    /**
     * Convenience method that returns the passed string transcribed.
     */
    public static String string(String in) {
	GreekTranscribe gt = new GreekTranscribe(new StringReader(in));
	StringBuilder result = new StringBuilder();
	try {
	    String s;
	    while ((s = gt.transcribe()) != null)
		result.append(s);
	} catch (IOException e) {
            return null;
        }
	return result.toString();
    }
%}

%init{
    outputStressed(false);
%init}


NOTE3	= [βγδζλμνραάεέηήιίϊΐοόυύϋΰωώΒΓΔΖΛΜΝΡΑΆΕΈΗΉΙΊΟΌΥΎΩΏ]
NOTE4	= [^βγδζλμνραάεέηήιίϊΐοόυύϋΰωώΒΓΔΖΛΜΝΡΑΆΕΈΗΉΙΊΟΌΥΎΩΏ]
GRLCASE	= [αάβγδεέζηήθιίϊΐκλμνξοόπρστυύϋΰφχψωώ]
GRUCASE	= [ΑΆΒΓΔΕΈΖΗΉΘΙΊΪΚΛΜΝΞΟΌΠΡΣΤΥΎΫΦΧΨΩΏ]
NOGREEK	= [^αάβγδεέζηήθιίϊΐκλμνξοόπρστυύϋΰφχψωώΑΆΒΓΔΕΈΖΗΉΘΙΊΪΚΛΜΝΞΟΌΠΡΣΤΥΎΫΦΧΨΩΏ]

%function transcribe
%type String

%unicode


%%

α		{ return "a"; }
ά		{ return stress_a; }
Α		{ return "A"; }
Ά		{ return stress_A; }

αυ/{NOTE3}	{ return "av"; }
Αυ/{NOTE3}	{ return "Av"; }
ΑΥ/{NOTE3}	{ return "AV"; }
αΥ/{NOTE3}	{ return "aV"; }
αυ/{NOTE4}	{ return "af"; }
Αυ/{NOTE4}	{ return "Af"; }
ΑΥ/{NOTE4}	{ return "AF"; }
αΥ/{NOTE4}	{ return "aF"; }

αύ/{NOTE3}	{ return stress_a + "v"; /* Note 10 */ }
Αύ/{NOTE3}	{ return stress_A + "v"; }
ΑΎ/{NOTE3}	{ return stress_A + "V"; }
αΎ/{NOTE3}	{ return stress_a + "V"; }
αύ/{NOTE4}	{ return stress_a + "f"; }
Αύ/{NOTE4}	{ return stress_A + "f"; }
ΑΎ/{NOTE4}	{ return stress_A + "F"; }
αΎ/{NOTE4}	{ return stress_a + "f"; }

β		{ return "v"; }
Β		{ return "V"; }

γ		{ return "g"; }
Γ		{ return "G"; }

γγ		{ return "ng"; }
γΓ		{ return "nG"; }
Γγ		{ return "Ng"; }
ΓΓ		{ return "NG"; }

γξ		{ return "nx"; }
γΞ		{ return "nX"; }
Γξ		{ return "Nx"; }
ΓΞ		{ return "NX"; }

γχ		{ return "nch"; }
γΧ		{ return "nCH"; }
Γχ		{ return "Nch"; }
ΓΧ		{ return "NCH"; }

δ		{ return "d"; }
Δ		{ return "D"; }

ε		{ return "e"; }
Ε		{ return "E"; }
έ		{ return stress_e; }
Έ		{ return stress_E; }

ευ/{NOTE3}	{ return "ev"; }
Ευ/{NOTE3}	{ return "Ev"; }
ΕΥ/{NOTE3}	{ return "EV"; }
εΥ/{NOTE3}	{ return "eV"; }
ευ/{NOTE4}	{ return "ef"; }
Ευ/{NOTE4}	{ return "Ef"; }
ΕΥ/{NOTE4}	{ return "EF"; }
εΥ/{NOTE4}	{ return "eF"; }

εύ/{NOTE3}	{ return stress_e + "v"; /* Note 10 */ }
Εύ/{NOTE3}	{ return stress_E + "v"; }
ΕΎ/{NOTE3}	{ return stress_E + "V"; }
εΎ/{NOTE3}	{ return stress_e + "V"; }
εύ/{NOTE4}	{ return stress_e + "f"; }
Εύ/{NOTE4}	{ return stress_E + "f"; }
ΕΎ/{NOTE4}	{ return stress_E + "F"; }
εΎ/{NOTE4}	{ return stress_e + "f"; }

ζ		{ return "z"; }
Ζ		{ return "Z"; }

η		{ return "i"; }
Η		{ return "I"; }
ή		{ return stress_i; }
Ή		{ return stress_I; }

ηυ/{NOTE3}	{ return "iv"; }
Ηυ/{NOTE3}	{ return "Ev"; }
ΗΥ/{NOTE3}	{ return "EV"; }
ηΥ/{NOTE3}	{ return "iV"; }
ηυ/{NOTE4}	{ return "if"; }
Ηυ/{NOTE4}	{ return "Ef"; }
ΗΥ/{NOTE4}	{ return "EF"; }
ηΥ/{NOTE4}	{ return "iF"; }

ηύ/{NOTE3}	{ return stress_i + "v"; /* Note 10 */ }
Ηύ/{NOTE3}	{ return stress_I + "v"; }
ΗΎ/{NOTE3}	{ return stress_I + "V"; }
ηΎ/{NOTE3}	{ return stress_i + "V"; }
ηύ/{NOTE4}	{ return stress_i + "f"; }
Ηύ/{NOTE4}	{ return stress_I + "f"; }
ΗΎ/{NOTE4}	{ return stress_I + "F"; }
ηΎ/{NOTE4}	{ return stress_i + "F"; }

Θ		{ return "TH"; }
θ		{ return "th"; }
Θ/{GRLCASE}	{ return "Th"; /* Note 11 */}

ι		{ return "i"; }
ί		{ return stress_i; }
ϊ		{ return diair_i; }
ΐ		{ return stress_i; }
Ι		{ return "I"; }
Ί		{ return stress_I; }
Ϊ		{ return diair_I; }

κ		{ return "k"; }
Κ		{ return "K"; }

λ		{ return "l"; }
Λ		{ return "L"; }

μ		{ return "m"; }
Μ		{ return "M"; }

{NOGREEK}μπ	{ return yytext() + "b"; /* Note 5 */ }
{NOGREEK}Μπ	{ return yytext() + "B"; }
{NOGREEK}μΠ	{ return yytext() + "b"; }
{NOGREEK}ΜΠ	{ return yytext() + "B"; }
^μπ		{ return "b"; }
^Μπ		{ return "B"; }
^μΠ		{ return "b"; }
^ΜΠ		{ return "B"; }

μπ/{NOGREEK}	{ return "b"; /* Note 7 */ }
Μπ/{NOGREEK}	{ return "B"; }
μΠ/{NOGREEK}	{ return "b"; }
ΜΠ/{NOGREEK}	{ return "B"; }

ν		{ return "n"; }
Ν		{ return "N"; }

Ξ		{ return "X"; }
ξ		{ return "x"; }


ο		{ return "o"; }
Ο		{ return "O"; }
ό		{ return stress_o; }
Ό		{ return stress_O; }

ου		{ return "ou"; }
Ου		{ return "Ou"; }
οΥ		{ return "oU"; }
ΟΥ		{ return "OU"; }

ού		{ return "o" + stress_u; }
Ού		{ return "O" + stress_u; }
οΎ		{ return "o" + stress_U; }
ΟΎ		{ return "O" + stress_U; }

π		{ return "p"; }
Π		{ return "P"; }

ρ		{ return "r"; }
Ρ		{ return "R"; }

σ		{ return "s"; }
ς		{ return "s"; }
Σ		{ return "S"; }

τ		{ return "t"; }
Τ		{ return "T"; }

υ		{ return "y"; }
Υ		{ return "Y"; }
ύ		{ return stress_y; }
ϋ		{ return diair_y; }
ΰ		{ return stress_y; }
Ύ		{ return stress_Y; }
Ϋ		{ return diair_Y; }

φ		{ return "f"; }
Φ		{ return "F"; }

χ		{ return "ch"; }
Χ		{ return "CH"; }
Χ/{GRLCASE}	{ return "Ch"; /* Note 11 */}

ψ		{ return "ps"; }
Ψ		{ return "PS"; }
Ψ/{GRLCASE}	{ return "Ps"; /* Note 11 */}

ω		{ return "o"; }
Ω		{ return "O"; }
ώ		{ return stress_o; }
Ώ		{ return stress_O; }

.		{ return yytext(); }
\n		{ return yytext(); }

<<EOF>>		{ return null; }

