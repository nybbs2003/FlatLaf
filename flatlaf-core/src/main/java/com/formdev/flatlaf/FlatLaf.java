/*
 * Copyright 2019 FormDev Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.formdev.flatlaf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.MetalLookAndFeel;
import com.formdev.flatlaf.util.SystemInfo;

/**
 * The base class for all Flat LaFs.
 *
 * @author Karl Tauber
 */
public abstract class FlatLaf
	extends BasicLookAndFeel
{
	private BasicLookAndFeel base;

	@Override
	public String getID() {
		return getName();
	}

	@Override
	public boolean isNativeLookAndFeel() {
		return true;
	}

	@Override
	public boolean isSupportedLookAndFeel() {
		return true;
	}

	@Override
	public void initialize() {
		getBase().initialize();

		super.initialize();
	}

	@Override
	public void uninitialize() {
		if( base != null )
			base.uninitialize();

		super.uninitialize();
	}

	/**
	 * Get/create base LaF. This is used to grab base UI defaults from different LaFs.
	 * E.g. on Mac from system dependent LaF, otherwise from Metal LaF.
	 */
	private BasicLookAndFeel getBase() {
		if( base == null )
			base = new MetalLookAndFeel();
		return base;
	}

	@Override
	public UIDefaults getDefaults() {
		UIDefaults defaults = getBase().getDefaults();

		initFonts( defaults );
		loadDefaultsFromProperties( defaults );

		return defaults;
	}

	private void initFonts( UIDefaults defaults ) {
		FontUIResource uiFont = null;

		//TODO
		if( SystemInfo.IS_WINDOWS ) {
			Font winFont = (Font) Toolkit.getDefaultToolkit().getDesktopProperty( "win.messagebox.font" );
			if( winFont != null )
				uiFont = new FontUIResource( winFont );
		}

		if( uiFont == null )
			return;

		// override fonts
		for( Object key : defaults.keySet() ) {
			if( key instanceof String && ((String)key).endsWith( ".font" ) )
				defaults.put( key, uiFont );
		}
	}

	/**
	 * Load properties associated to Flat LaF classes and add to UI defaults.
	 *
	 * Each class that extend this class may have its own .properties file
	 * in the same package as the class. Properties from superclasses are loaded
	 * first to give subclasses a chance to override defaults.
	 * E.g. if running FlatDarkLaf, then the FlatLaf.properties is loaded first
	 * and FlatDarkLaf.properties loaded second.
	 */
	private void loadDefaultsFromProperties( UIDefaults defaults ) {
		// determine classes in class hierarchy in reverse order
		ArrayList<Class<?>> lafClasses = new ArrayList<>();
		for( Class<?> lafClass = getClass();
			FlatLaf.class.isAssignableFrom( lafClass );
			lafClass = lafClass.getSuperclass() )
		{
			lafClasses.add( 0, lafClass );
		}

		try {
			// load properties files
			Properties properties = new Properties();
			for( Class<?> lafClass : lafClasses ) {
				String propertiesName = "/" + lafClass.getName().replace( '.', '/' ) + ".properties";
				try( InputStream in = lafClass.getResourceAsStream( propertiesName ) ) {
					if( in != null )
						properties.load( in );
				}
			}

			// get globals, which override all other defaults that end with same suffix
			String globalPrefix = "*.";
			HashMap<String, Object> globals = new HashMap<>();
			for( Object okey : properties.keySet() ) {
				String key = (String) okey;
				if( key.startsWith( globalPrefix ) ) {
					Object value = parseValue( key, properties.getProperty( key ) );
					globals.put( key.substring( globalPrefix.length() ), value );
				}
			}

			// override UI defaults with globals
			for( Object key : defaults.keySet() ) {
				if( key instanceof String && ((String)key).contains( "." ) ) {
					String skey = (String) key;
					String globalKey = skey.substring( skey.lastIndexOf( '.' ) + 1 );
					Object globalValue = globals.get( globalKey );
					if( globalValue != null )
						defaults.put( key, globalValue );
				}
			}

			// add non-global properties to UI defaults
			for( Map.Entry<Object, Object> e : properties.entrySet() ) {
				String key = (String) e.getKey();
				String value = (String) e.getValue();
				defaults.put( key, parseValue( key, value ) );
			}
		} catch( IOException ex ) {
			ex.printStackTrace();
		}
	}

	private Object parseValue( String key, String value ) {
		value = value.trim();

		// null, false, true
		switch( value ) {
			case "null":		return null;
			case "false":	return false;
			case "true":		return true;
		}

		// colors
		ColorUIResource color = parseColor( value );
		if( color != null )
			return color;

		// string
		return value;
	}

	private ColorUIResource parseColor( String value ) {
		try {
			if( value.length() == 6 ) {
				int rgb = Integer.parseInt( value, 16 );
				return new ColorUIResource( rgb );
			}
			if( value.length() == 8 ) {
				int rgba = Integer.parseInt( value, 16 );
				return new ColorUIResource( new Color( rgba, true ) );
			}
		} catch( NumberFormatException ex ) {
			// not a color --> ignore
		}
		return null;
	}
}
