/**************************************************************************************
 http://code.google.com/a/apache-extras.org/p/camel-extra

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.


 You should have received a copy of the GNU Lesser General Public
 License along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 02110-1301, USA.

 http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ***************************************************************************************/
package org.apacheextras.camel.jpa.jmx;

import org.junit.Test;

import javax.management.openmbean.OpenDataException;

import static org.junit.Assert.fail;

public class OpenTypeSupportTest {

  @Test
  public void getFactoryTest() {
    try {
      OpenTypeSupport.getFactory(OpenTypeSupportTest.class);
    } catch (OpenDataException e) {
      fail("Failed to great OpenTypeSupport factory:\n" + e.getMessage());
    }
  }
}