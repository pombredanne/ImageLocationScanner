package com.veggiespam.imagelocationscanner;

import java.io.File;
import java.io.FileInputStream;
//import java.io.FileOutputStream;	// Only needed when debugging the code
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.lang.GeoLocation;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpDescriptor;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.iptc.IptcDescriptor;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDirectory;
import com.drew.metadata.exif.makernotes.PanasonicMakernoteDescriptor;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDescriptor;
import com.drew.metadata.exif.makernotes.SigmaMakernoteDirectory;
import com.drew.metadata.exif.makernotes.SigmaMakernoteDescriptor;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDirectory;
import com.drew.metadata.exif.makernotes.NikonType2MakernoteDescriptor;
import com.drew.metadata.exif.makernotes.OlympusMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusMakernoteDescriptor;
import com.drew.metadata.exif.makernotes.OlympusEquipmentMakernoteDirectory;
import com.drew.metadata.exif.makernotes.OlympusEquipmentMakernoteDescriptor;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDirectory;
import com.drew.metadata.exif.makernotes.FujifilmMakernoteDescriptor;

/**
 * Image Location Scanner main static class.  Passively scans an image data stream (jpg/png/etc)
 * and reports if the image contains embedded location information, such as Exif GPS, IPTC codes, and
 * some proprietary camera codes.  This class is designed to be a plug-in for both ZAP and Burp proxies.
 * 
 * @author  Jay Ball / github: veggiespam / twitter: @veggiespam / www.veggiespam.com
 * @license Apache License 2.0
 * @version 0.3
 * @see https://www.veggiespam.com/ils/
 */
public class ILS {

	/** A bunch of static strings that are used by both ZAP and Burp plug-ins. */
    public static final String pluginName = "Image Location and Privary Scanner";
    public static final String pluginVersion = "0.3";
    public static final String alertTitle = "Image Exposes Location or PII Data";
    public static final String alertDetailPrefix = "This image embeds a location or leaks privacy-related data: ";
    public static final String alertBackground 
    	= "The image was found to contain embedded location information, such as GPS coordinates, or "
		+ "another privacy exposure, such as camera serial number.  "
    	+ "Depending on the context of the image in the website, "
    	+ "this information may expose private details of the users of a site.  For example, a site that allows "
    	+ "users to upload profile pictures taken in the home may expose the home's address.  ";
    public static final String remediationBackground 
    	= "Before allowing images to be stored on the server and/or transmitted to the browser, strip out the "
    	+ "embedded location information from image.  This could mean removing all Exif data or just the GPS "
    	+ "component.  Other data, like serial numbers, should also be removed.";
    public static final String remediationDetail = null;
    public static final String referenceURL = "https://www.veggiespam.com/ils/"; 
    public static final String pluginAuthor = "Jay Ball (veggiespam)"; 

	private static final String EmptyString = "";
	private static final String Space = " ";
	private static final String Seperator = " ||  ";  // one space at start, two at end
	private static final String TextSubtypeEnd = ": "; // colon space for plain text results

	private static final String HTML_subtype_begin = "<li>";
	private static final String HTML_subtype_title_end = "\n\t<ul>\n";
	private static final String HTML_subtype_end = "\t</ul></li>\n";

	private static final String HTML_finding_begin = "\t<li>";
	private static final String HTML_finding_end = "</li>\n";

	public ILS() {
		// blank constructor
		super();
	}
	
    
	/** Tests a data blob for Location or GPS information and returns the image location
	 * information as a string.  If no location is present or there is an error,
	 * the function will return an empty string of "".
	 * 
	 * @param data is a byte array that is an image file to test, such as entire jpeg file.
	 * @return String containing the Location data or an empty String indicating no GPS data found.
	 */
    public static String[] scanForLocationInImageBoth(byte[] data)   {
		String[] results = { EmptyString, EmptyString };
    	
    	/*  // Extreme debugging code for making sure data from Burp/ZAP/newproxy gets into 
			// ILS.  This code is very slow and not to be compiled in, even with if(debug)
			// types of contrusts.  This code this will save the image file to disk for binary
			// import debugging.  
		String t[] = new String[2];
    	try{
       		FileOutputStream o = new FileOutputStream(new File("/tmp/output.jpg"));
			o.write(data);
			o.close();
    	} catch (IOException e) {
			t[0] = "IOException Exception " + e.toString();
			t[1] = t[0];
    		return t;
    	}
		t[0] = "Scanning " + data.length + "\n\n";
		t[1] = t[0];
    	// return t;   /*   --- if you use this line, remember to comment out rest of function.
		*/	
 
    	try {
			BufferedInputStream is = new BufferedInputStream(new ByteArrayInputStream(data, 0, data.length));
			Metadata md = ImageMetadataReader.readMetadata(is);

			String[] tmp = { EmptyString, EmptyString };

			tmp = scanForLocation(md);
			results = scanForPrivacy(md);

			if (tmp[0].length() > 0) {
				results[0] = tmp[0] + "\n\n" + results[0];
				results[1] = "<ul>"  +  tmp[1] + results[1] + "</ul>";

				// AGAIN: this is for extreme debugging
				// results[0] = "DBG: " + t[0] + "\n\n" + results[0];
				// results[1] = "DBG: " + t[1] + "\n\n" + results[1]; 
			}


    	} catch (ImageProcessingException e) {
    		// bad image, just ignore processing exceptions
    		// DEBUG: return new String("ImageProcessingException " + e.toString());
    	} catch (IOException e) {
    		// bad file or something, just ignore 
    		// DEBUG: return new String("IOException " + e.toString());
    	}

    	return results; 
	}


	/** Returns ILS information as HTML formatting string.
	 * 
	 * @see scanForLocationInImageBoth
	 */
    public static String scanForLocationInImageHTML(byte[] data)   {
		return scanForLocationInImageBoth(data)[1];
	}

	/** Returns ILS information as Text formatting string.
	 * 
	 * @see scanForLocationInImageBoth
	 */
    public static String scanForLocationInImageText(byte[] data)   {
		return scanForLocationInImageBoth(data)[0];
	}


	/** 
	 * @deprecated Use the HTML / Text calls directly or use boolean construct.
	 */
    public static String scanForLocationInImage(byte[] data)   {
		return scanForLocationInImageHTML(data);
	}

	/** Returns ILS informtion in Text or HTML depending on usehtml flag.
	 * 
	 * @param data is a byte array that is an image file to test, such as entire jpeg file.
	 * @param usehtml output as html (true) or plain txt (false)
	 * @return String containing the Location data or an empty String indicating no GPS data found.
	 * @see scanForLocationInImageBoth
	 */
    public static String scanForLocationInImage(byte[] data, boolean usehtml)   {
		if (usehtml) {
			return scanForLocationInImageHTML(data);
		} else {
			return scanForLocationInImageText(data);
		}
	}


    private static String[] appendResults(String current[], String bigtype, String subtype, ArrayList<String> exposure)   {
		String[] tmp = formatResults(bigtype, subtype, exposure);

		if (tmp[0].length() > 0) {
			current[0] = current[0] + tmp[0];
			current[1] = current[1] + tmp[1];
		}
		return current;
	}

    private static String[] formatResults(String bigtype, String subtype, ArrayList<String> exposure)   {
		StringBuffer ret = new StringBuffer(200);
		StringBuffer retHTML = new StringBuffer(200);
		String[] retarr = { EmptyString, EmptyString };

		if (exposure.size() > 0) {
			retHTML.append(HTML_subtype_begin).append(bigtype).append(" / ").append(subtype).append(HTML_subtype_title_end);
			for (String finding : exposure) {
				ret.append(subtype).append(TextSubtypeEnd).append(finding).append("\n");
				retHTML.append(HTML_finding_begin).append(finding).append(HTML_finding_end);
			}
			retHTML.append(HTML_subtype_end);
		}

		retarr[0] = ret.toString();
		retarr[1] = retHTML.toString();

		return retarr;
	}




    public static String[] scanForLocation(Metadata md)   {
    	ArrayList<String> retarr = new ArrayList<String>();
    	//ArrayList<String> retHTML = new ArrayList<String>();
		ArrayList<String> exposure = new ArrayList<String>();

		StringBuffer ret = new StringBuffer(200);
		StringBuffer retHTML = new StringBuffer(200);

		String[] results = { EmptyString, EmptyString };

		String bigtype = "Location";  // Overall category type.  Location or Privacy
		String subtype = EmptyString;

		// ** Standard Exif GPS
		subtype = "Exif_GPS";
		Collection<GpsDirectory> gpsDirColl = md.getDirectoriesOfType(GpsDirectory.class);

		if (gpsDirColl != null) {
			exposure.clear();

			for (GpsDirectory gpsDir : gpsDirColl) {
				final GeoLocation geoLocation = gpsDir.getGeoLocation();
				if ( ! (geoLocation == null || geoLocation.isZero()) ) {
					String finding = geoLocation.toDMSString();

					exposure.add(finding);
				}
			}
			results = appendResults(results, bigtype, subtype, exposure);
		}


		// ** IPTC testing
		subtype = "IPTC";
		Collection<IptcDirectory> iptcDirColl = md.getDirectoriesOfType(IptcDirectory.class);

		int iptc_tag_list[] = {
			IptcDirectory.TAG_CITY,
			IptcDirectory.TAG_CONTENT_LOCATION_CODE,
			IptcDirectory.TAG_CONTENT_LOCATION_NAME,
			IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION_CODE,
			IptcDirectory.TAG_COUNTRY_OR_PRIMARY_LOCATION_NAME,
			IptcDirectory.TAG_DESTINATION,
		};

		if (iptcDirColl != null) {
			exposure.clear();

			for (IptcDirectory iptcDir : iptcDirColl) {
				IptcDescriptor iptcDesc = new IptcDescriptor(iptcDir);
				for (int i=0; i< iptc_tag_list.length; i++) {
					String tag = iptcDesc.getDescription(iptc_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						exposure.add( iptcDir.getTagName(iptc_tag_list[i]) + " = " + tag );
						String element = bigtype + subtype + iptcDir.getTagName(iptc_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}

		// ** Proprietary camera: Panasonic / Lumix
		subtype = "Panasonic";
		Collection<PanasonicMakernoteDirectory> panasonicDirColl = md.getDirectoriesOfType(PanasonicMakernoteDirectory.class);

		int panasonic_tag_list[] = {
			PanasonicMakernoteDirectory.TAG_CITY,
			PanasonicMakernoteDirectory.TAG_COUNTRY,
			PanasonicMakernoteDirectory.TAG_LANDMARK,
			PanasonicMakernoteDirectory.TAG_LOCATION,
			PanasonicMakernoteDirectory.TAG_STATE,
		};

		if (panasonicDirColl != null) {
			exposure.clear();

			for (PanasonicMakernoteDirectory panasonicDir : panasonicDirColl) {
				PanasonicMakernoteDescriptor descriptor = new PanasonicMakernoteDescriptor(panasonicDir);
				for (int i=0; i< panasonic_tag_list.length; i++) {
					String tag = descriptor.getDescription(panasonic_tag_list[i]);
					// Panasonic occationally uses "---" when it cannot find info, we choose to strip it out.
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.equals("---") || tag.charAt(0) == '\0' )) {
						exposure.add(panasonicDir.getTagName(panasonic_tag_list[i]) + " = " + tag);
						String element = bigtype + subtype + panasonicDir.getTagName(panasonic_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}

		// For Text, add the big type in the final entry
		if (results[0].length() > 0) {
			results[0] = bigtype + ":: " + results[0];
		}

		return results;
    }


    public static String[] scanForPrivacy(Metadata md)   {
    	ArrayList<String> retarr = new ArrayList<String>();
		String bigtype = "Privacy";  // Overall category type.
		String subtype = EmptyString;
		ArrayList<String> exposure = new ArrayList<String>();

		String[] results = { EmptyString, EmptyString };


		// ** XMP testing
		subtype = "XMP";
		Collection<XmpDirectory> xmpDirColl = md.getDirectoriesOfType(XmpDirectory.class);

		int xmp_tag_list[] = {
			XmpDirectory.TAG_CAMERA_SERIAL_NUMBER 
		};

		if (xmpDirColl != null) {
			exposure.clear();

			for (XmpDirectory xmpDir : xmpDirColl) {
				XmpDescriptor xmpDesc = new XmpDescriptor(xmpDir);
				for (int i=0; i< xmp_tag_list.length; i++) {
					String tag = xmpDesc.getDescription(xmp_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + xmpDir.getTagName(xmp_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
				results = appendResults(results, bigtype, subtype, exposure);
			}
		}


		// ** IPTC testing
		subtype = "IPTC";
		Collection<IptcDirectory> iptcDirColl = md.getDirectoriesOfType(IptcDirectory.class);

		int iptc_tag_list[] = {
			IptcDirectory.TAG_KEYWORDS,
			IptcDirectory.TAG_LOCAL_CAPTION
			// what about CREDIT   BY_LINE  ...
		};

		if (iptcDirColl != null) {
			exposure.clear();

			for (IptcDirectory iptcDir : iptcDirColl) {
				IptcDescriptor iptcDesc = new IptcDescriptor(iptcDir);
				for (int i=0; i< iptc_tag_list.length; i++) {
					String tag = iptcDesc.getDescription(iptc_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						exposure.add( iptcDir.getTagName(iptc_tag_list[i]) + " = " + tag );
						String element = bigtype + subtype + iptcDir.getTagName(iptc_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}

		// ** Proprietary camera: Panasonic / Lumix
		subtype = "Panasonic";
		Collection<PanasonicMakernoteDirectory> panasonicDirColl = md.getDirectoriesOfType(PanasonicMakernoteDirectory.class);

		int panasonic_tag_list[] = {
			PanasonicMakernoteDirectory.TAG_BABY_AGE,
			PanasonicMakernoteDirectory.TAG_BABY_AGE_1,
			PanasonicMakernoteDirectory.TAG_BABY_NAME,
			PanasonicMakernoteDirectory.TAG_INTERNAL_SERIAL_NUMBER,
			PanasonicMakernoteDirectory.TAG_LENS_SERIAL_NUMBER 
			// What about   TAG_TEXT_STAMP_*  TAG_TITLE 
		};

		if (panasonicDirColl != null) {
			exposure.clear();

			for (PanasonicMakernoteDirectory panasonicDir: panasonicDirColl) {
				PanasonicMakernoteDescriptor descriptor = new PanasonicMakernoteDescriptor(panasonicDir);
				for (int i=0; i< panasonic_tag_list.length; i++) {
					String tag = descriptor.getDescription(panasonic_tag_list[i]);
					// Panasonic occationally uses "---" when it cannot find info, we choose to strip it out.
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.equals("---") || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + panasonicDir.getTagName(panasonic_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}



		// ** Proprietary camera: Olympus
		subtype = "Olympus";
		Collection<OlympusMakernoteDirectory> olympusDirColl = md.getDirectoriesOfType(OlympusMakernoteDirectory.class);

		int olympus_tag_list[] = {
			OlympusMakernoteDirectory.TAG_SERIAL_NUMBER
		};

		if (olympusDirColl != null) {
			exposure.clear();

			for (OlympusMakernoteDirectory olympusDir: olympusDirColl) {
				OlympusMakernoteDescriptor descriptor = new OlympusMakernoteDescriptor(olympusDir);
				for (int i=0; i< olympus_tag_list.length; i++) {
					String tag = descriptor.getDescription(olympus_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + olympusDir.getTagName(olympus_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}

		// ** Proprietary camera: OlympusEquipment
		subtype = "OlympusEquipment";
		Collection<OlympusEquipmentMakernoteDirectory> olympusEquipmentDirColl = md.getDirectoriesOfType(OlympusEquipmentMakernoteDirectory.class);

		int olympusEquipment_tag_list[] = {
			OlympusEquipmentMakernoteDirectory.TAG_SERIAL_NUMBER,
			OlympusEquipmentMakernoteDirectory.TAG_INTERNAL_SERIAL_NUMBER,
			OlympusEquipmentMakernoteDirectory.TAG_LENS_SERIAL_NUMBER,
			OlympusEquipmentMakernoteDirectory.TAG_EXTENDER_SERIAL_NUMBER,
			OlympusEquipmentMakernoteDirectory.TAG_FLASH_SERIAL_NUMBER
		};

		if (olympusEquipmentDirColl != null) {
			exposure.clear();

			for (OlympusEquipmentMakernoteDirectory olympusEquipmentDir: olympusEquipmentDirColl) {
				OlympusEquipmentMakernoteDescriptor descriptor = new OlympusEquipmentMakernoteDescriptor(olympusEquipmentDir);
				for (int i=0; i< olympusEquipment_tag_list.length; i++) {
					String tag = descriptor.getDescription(olympusEquipment_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + olympusEquipmentDir.getTagName(olympusEquipment_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}


		// ** Proprietary camera: Canon
		subtype = "Canon";
		Collection<CanonMakernoteDirectory> canonDirColl = md.getDirectoriesOfType(CanonMakernoteDirectory.class);

		int canon_tag_list[] = {
			CanonMakernoteDirectory.TAG_CANON_OWNER_NAME, 
			CanonMakernoteDirectory.TAG_CANON_SERIAL_NUMBER
		};

		if (canonDirColl != null) {
			exposure.clear();

			for (CanonMakernoteDirectory canonDir: canonDirColl) {
				CanonMakernoteDescriptor descriptor = new CanonMakernoteDescriptor(canonDir);
				for (int i=0; i< canon_tag_list.length; i++) {
					String tag = descriptor.getDescription(canon_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + canonDir.getTagName(canon_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}




		// ** Proprietary camera: Sigma
		subtype = "Sigma";
		Collection<SigmaMakernoteDirectory> sigmaDirColl = md.getDirectoriesOfType(SigmaMakernoteDirectory.class);

		int sigma_tag_list[] = {
			SigmaMakernoteDirectory.TAG_SERIAL_NUMBER
		};

		if (sigmaDirColl != null) {
			exposure.clear();

			for (SigmaMakernoteDirectory sigmaDir: sigmaDirColl) {
				SigmaMakernoteDescriptor descriptor = new SigmaMakernoteDescriptor(sigmaDir);
				for (int i=0; i< sigma_tag_list.length; i++) {
					String tag = descriptor.getDescription(sigma_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + sigmaDir.getTagName(sigma_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}




		// ** Proprietary camera: Nikon
		subtype = "Nikon";
		Collection<NikonType2MakernoteDirectory> nikonDirColl = md.getDirectoriesOfType(NikonType2MakernoteDirectory.class);

		int nikon_tag_list[] = {
			NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER,
			NikonType2MakernoteDirectory.TAG_CAMERA_SERIAL_NUMBER_2
		};

		if (nikonDirColl != null) {
			exposure.clear();

			for (NikonType2MakernoteDirectory nikonDir: nikonDirColl) {
				NikonType2MakernoteDescriptor descriptor = new NikonType2MakernoteDescriptor(nikonDir);
				for (int i=0; i< nikon_tag_list.length; i++) {
					String tag = descriptor.getDescription(nikon_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + nikonDir.getTagName(nikon_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}


		// ** Proprietary camera: FujiFilm
		subtype = "FujiFilm";
		Collection<FujifilmMakernoteDirectory> fujifilmDirColl = md.getDirectoriesOfType(FujifilmMakernoteDirectory.class);

		int fujifilm_tag_list[] = {
			FujifilmMakernoteDirectory.TAG_SERIAL_NUMBER
		};

		if (fujifilmDirColl != null) {
			exposure.clear();

			for (FujifilmMakernoteDirectory fujifilmDir: fujifilmDirColl) {
				FujifilmMakernoteDescriptor descriptor = new FujifilmMakernoteDescriptor(fujifilmDir);
				for (int i=0; i< fujifilm_tag_list.length; i++) {
					String tag = descriptor.getDescription(fujifilm_tag_list[i]);
					if ( ! ( null == tag || tag.equals(EmptyString) || tag.charAt(0) == '\0' )) {
						String element = bigtype + subtype + fujifilmDir.getTagName(fujifilm_tag_list[i]) + " = " + tag;
						retarr.add(element);
					}
				}
			}

			results = appendResults(results, bigtype, subtype, exposure);
		}
		
		if (results[0].length() > 0) {
			results[0] = bigtype + ":: " + results[0];
		}
		return results;
    }


    
    public static void main(String[] args) throws Exception {
		boolean html = false;
    	if (args.length == 0){
    		System.out.println("Java Image Location Scanner");
    		System.out.println("Usage: java ILS.class [-h|-t] file1.jpg file2.png file3.txt [...]");
    		System.out.println("\t-h : optional specifer to output results in HTML format");
    		System.out.println("\t-t : optional specifer to output results in plain text format");
    		return;
    	}
    	for (String s: args) {
			if (s.equals("-h")) {
				html=true;
				continue;
			}
			if (s.equals("-t")) {
				html=false;
				continue;
			}
            try {
				System.out.print("Processing " + s + " : ");

				File f = new File(s);
				FileInputStream fis = new FileInputStream(f);
				long size = f.length();
				byte[] data = new byte[(int) size];
				fis.read(data);
				fis.close();
				
				String res = scanForLocationInImage(data, html);
				if (0 == res.length())  {
					res = "None";
				}
				System.out.println(res);
        	} catch (IOException e) {
        		System.out.println(e.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    }
}
