/* 
 * Copyright (C) 2013 Lisa Park, Inc. (www.lisa-park.net)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.lisapark;

import org.lisapark.octopus.core.parameter.ConversionException;
import org.openide.util.Exceptions;

public class App {

    public static void main(String[] args) {
        
        int value = 0;
        try {
            value = parseValueFromString("12.0");
        } catch (ConversionException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        System.out.println("Hello World!" + value);

    }
    
    public static Integer parseValueFromString(String stringValue) throws ConversionException {
        String str = stringValue;
        try {
            // Check for decimal dot - AM
            int endIndex = stringValue.indexOf('.');
            if(endIndex > 0) {
                str = stringValue.substring(0, endIndex);
            }
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throw new ConversionException(String.format("Could not convert %s into a number", stringValue));
        }
    }
}