package com.welie.blessed;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static com.welie.blessed.BluetoothBytesParser.FORMAT_FLOAT;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_SFLOAT;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_SINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_SINT32;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_SINT8;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT16;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT32;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static junit.framework.Assert.assertEquals;

import static android.os.Build.VERSION_CODES.M;
import static org.junit.Assert.assertArrayEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk = { M }, shadows={ShadowBluetoothLEAdapter.class} )
public class BluetoothBytesParserTest {

    @Before
    public void setUp()  {

    }

    @Test
    public void constructor1Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        assertEquals(LITTLE_ENDIAN, parser.getByteOrder());
        assertEquals(0, parser.getValue().length);
        assertEquals(0, parser.getOffset());
    }

    @Test
    public void constructor2Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1,2}, 1);
        assertEquals(LITTLE_ENDIAN, parser.getByteOrder());
        assertEquals(2, parser.getValue().length);
        assertEquals(1, parser.getOffset());
    }

    @Test (expected = IllegalArgumentException.class)
    public void getIntInvalidFormatTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1,2});
        parser.getIntValue(249);
    }

    @Test
    public void parseUINT8Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{40, (byte)200, (byte)255, 80});

        // Read using offsets
        assertEquals(40, (int) parser.getIntValue(FORMAT_UINT8, 0, LITTLE_ENDIAN));
        assertEquals(200, (int) parser.getIntValue(FORMAT_UINT8, 1, LITTLE_ENDIAN));
        assertEquals(255, (int) parser.getIntValue(FORMAT_UINT8, 2, LITTLE_ENDIAN));
        assertEquals(80, (int) parser.getIntValue(FORMAT_UINT8, 3, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(40, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(200, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(255, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(80, (int) parser.getIntValue(FORMAT_UINT8));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(40, (int) parser.getIntValue(FORMAT_UINT8, LITTLE_ENDIAN));
        assertEquals(200, (int) parser.getIntValue(FORMAT_UINT8, LITTLE_ENDIAN));
        assertEquals(255, (int) parser.getIntValue(FORMAT_UINT8, LITTLE_ENDIAN));
        assertEquals(80, (int) parser.getIntValue(FORMAT_UINT8, LITTLE_ENDIAN));
    }

    @Test
    public void parseSINT8Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{40, (byte)-40, (byte)127, -127});

        // Read using offsets
        assertEquals(40, (int) parser.getIntValue(FORMAT_SINT8, 0, LITTLE_ENDIAN));
        assertEquals(-40, (int) parser.getIntValue(FORMAT_SINT8, 1, LITTLE_ENDIAN));
        assertEquals(127, (int) parser.getIntValue(FORMAT_SINT8, 2, LITTLE_ENDIAN));
        assertEquals(-127, (int) parser.getIntValue(FORMAT_SINT8, 3, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(40, (int) parser.getIntValue(FORMAT_SINT8));
        assertEquals(-40, (int) parser.getIntValue(FORMAT_SINT8));
        assertEquals(127, (int) parser.getIntValue(FORMAT_SINT8));
        assertEquals(-127, (int) parser.getIntValue(FORMAT_SINT8));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(40, (int) parser.getIntValue(FORMAT_SINT8, LITTLE_ENDIAN));
        assertEquals(-40, (int) parser.getIntValue(FORMAT_SINT8, LITTLE_ENDIAN));
        assertEquals(127, (int) parser.getIntValue(FORMAT_SINT8, LITTLE_ENDIAN));
        assertEquals(-127, (int) parser.getIntValue(FORMAT_SINT8, LITTLE_ENDIAN));
    }

    @Test
    public void parseUINT16_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, 2, 3, 4});

        // Read using offsets
        assertEquals(513, (int) parser.getIntValue(FORMAT_UINT16, 0, LITTLE_ENDIAN));
        assertEquals(770, (int) parser.getIntValue(FORMAT_UINT16, 1, LITTLE_ENDIAN));
        assertEquals(1027, (int) parser.getIntValue(FORMAT_UINT16, 2, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(513, (int) parser.getIntValue(FORMAT_UINT16));
        assertEquals(1027, (int) parser.getIntValue(FORMAT_UINT16));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(513, (int) parser.getIntValue(FORMAT_UINT16, LITTLE_ENDIAN));
        assertEquals(1027, (int) parser.getIntValue(FORMAT_UINT16, LITTLE_ENDIAN));
    }

    @Test
    public void parseUINT16_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, 2, 3, 4}, BIG_ENDIAN);

        // Read using offsets
        assertEquals(258, (int) parser.getIntValue(FORMAT_UINT16, 0, BIG_ENDIAN));
        assertEquals(515, (int) parser.getIntValue(FORMAT_UINT16, 1, BIG_ENDIAN));
        assertEquals(772, (int) parser.getIntValue(FORMAT_UINT16, 2, BIG_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(258, (int) parser.getIntValue(FORMAT_UINT16));
        assertEquals(772, (int) parser.getIntValue(FORMAT_UINT16));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(258, (int) parser.getIntValue(FORMAT_UINT16, BIG_ENDIAN));
        assertEquals(772, (int) parser.getIntValue(FORMAT_UINT16, BIG_ENDIAN));
    }

    @Test
    public void parseSINT16_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, -2, 3, -4});

        // Read using offsets
        assertEquals(-511, (int) parser.getIntValue(FORMAT_SINT16, 0, LITTLE_ENDIAN));
        assertEquals(1022, (int) parser.getIntValue(FORMAT_SINT16, 1, LITTLE_ENDIAN));
        assertEquals(-1021, (int) parser.getIntValue(FORMAT_SINT16, 2, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(-511, (int) parser.getIntValue(FORMAT_SINT16));
        assertEquals(-1021, (int) parser.getIntValue(FORMAT_SINT16));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(-511, (int) parser.getIntValue(FORMAT_SINT16, LITTLE_ENDIAN));
        assertEquals(-1021, (int) parser.getIntValue(FORMAT_SINT16, LITTLE_ENDIAN));
    }

    @Test
    public void parseSINT16_BigEndianTest() {
        // Use byte array 0x01fe03fc
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, -2, 3, -4}, BIG_ENDIAN);

        // Read using offsets
        assertEquals(510, (int) parser.getIntValue(FORMAT_SINT16, 0, BIG_ENDIAN));
        assertEquals(-509, (int) parser.getIntValue(FORMAT_SINT16, 1, BIG_ENDIAN));
        assertEquals(1020, (int) parser.getIntValue(FORMAT_SINT16, 2, BIG_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(510, (int) parser.getIntValue(FORMAT_SINT16));
        assertEquals(1020, (int) parser.getIntValue(FORMAT_SINT16));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(510, (int) parser.getIntValue(FORMAT_SINT16, BIG_ENDIAN));
        assertEquals(1020, (int) parser.getIntValue(FORMAT_SINT16, BIG_ENDIAN));
    }

    @Test
    public void parseUINT32_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        // Read using offsets
        assertEquals(67305985, (int) parser.getIntValue(FORMAT_UINT32, 0, LITTLE_ENDIAN));
        assertEquals(84148994, (int) parser.getIntValue(FORMAT_UINT32, 1, LITTLE_ENDIAN));
        assertEquals(100992003, (int) parser.getIntValue(FORMAT_UINT32, 2, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(67305985, (int) parser.getIntValue(FORMAT_UINT32));
        assertEquals(134678021, (int) parser.getIntValue(FORMAT_UINT32));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(67305985, (int) parser.getIntValue(FORMAT_UINT32, LITTLE_ENDIAN));
        assertEquals(134678021, (int) parser.getIntValue(FORMAT_UINT32, LITTLE_ENDIAN));
    }

    @Test
    public void parseUINT32_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, BIG_ENDIAN);

        // Read using offsets
        assertEquals(16909060, (int) parser.getIntValue(FORMAT_UINT32, 0, BIG_ENDIAN));
        assertEquals(33752069, (int) parser.getIntValue(FORMAT_UINT32, 1, BIG_ENDIAN));
        assertEquals(50595078, (int) parser.getIntValue(FORMAT_UINT32, 2, BIG_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(16909060, (int) parser.getIntValue(FORMAT_UINT32));
        assertEquals(84281096, (int) parser.getIntValue(FORMAT_UINT32));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(16909060, (int) parser.getIntValue(FORMAT_UINT32, BIG_ENDIAN));
        assertEquals(84281096, (int) parser.getIntValue(FORMAT_UINT32, BIG_ENDIAN));
    }

    @Test
    public void parseSINT32_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, -2, 3, -4, 5, -6, 7, -8});

        // Read using offsets
        assertEquals(-66847231, (int) parser.getIntValue(FORMAT_SINT32, 0, LITTLE_ENDIAN));
        assertEquals(100402174, (int) parser.getIntValue(FORMAT_SINT32, 1, LITTLE_ENDIAN));
        assertEquals(-100271101, (int) parser.getIntValue(FORMAT_SINT32, 2, LITTLE_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(-66847231, (int) parser.getIntValue(FORMAT_SINT32));
        assertEquals(-133694971, (int) parser.getIntValue(FORMAT_SINT32));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(-66847231, (int) parser.getIntValue(FORMAT_SINT32, LITTLE_ENDIAN));
        assertEquals(-133694971, (int) parser.getIntValue(FORMAT_SINT32, LITTLE_ENDIAN));
    }

    @Test
    public void parseSINT32_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1, -2, 3, -4, 5, -6, 7, -8}, BIG_ENDIAN);

        // Read using offsets
        assertEquals(33424380, (int) parser.getIntValue(FORMAT_SINT32, 0, BIG_ENDIAN));
        assertEquals(-33293307, (int) parser.getIntValue(FORMAT_SINT32, 1, BIG_ENDIAN));
        assertEquals(66848250, (int) parser.getIntValue(FORMAT_SINT32, 2, BIG_ENDIAN));

        // Read using auto-advance offsets
        assertEquals(33424380, (int) parser.getIntValue(FORMAT_SINT32));
        assertEquals(100272120, (int) parser.getIntValue(FORMAT_SINT32));

        // Read using auto-advance and byte order
        parser.setOffset(0);
        assertEquals(33424380, (int) parser.getIntValue(FORMAT_SINT32, BIG_ENDIAN));
        assertEquals(100272120, (int) parser.getIntValue(FORMAT_SINT32, BIG_ENDIAN));
    }

    @Test
    public void parseLong_BigEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{0,0,0,0,0,0,0,1}, BIG_ENDIAN);
        assertEquals(1L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{0,0,0,0,0,0,1,1}, BIG_ENDIAN);
        assertEquals(257L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{1,1,1,1,1,1,1,1}, BIG_ENDIAN);
        assertEquals(72340172838076673L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{1,2,3,4,5,6,7,8}, BIG_ENDIAN);
        assertEquals(72623859790382856L, (long)parser.getLongValue());
    }

    @Test
    public void parseLong_LittleEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{1,0,0,0,0,0,0,0}, LITTLE_ENDIAN);
        assertEquals(1L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{1,1,0,0,0,0,0,0}, LITTLE_ENDIAN);
        assertEquals(257L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{1,1,1,1,1,1,1,1}, LITTLE_ENDIAN);
        assertEquals(72340172838076673L, (long) parser.getLongValue());

        parser = new BluetoothBytesParser(new byte[]{8,7,6,5,4,3,2,1}, LITTLE_ENDIAN);
        assertEquals(72623859790382856L, (long) parser.getLongValue());
    }

    @Test (expected = IllegalArgumentException.class)
    public void getFloatInvalidFormatTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{1,2,3,4});
        parser.getFloatValue(249);
    }

    @Test
    public void parseFloat_LittleEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{1,2,3,4}, LITTLE_ENDIAN);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT, LITTLE_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT, 0, LITTLE_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{1,-2,3,-4}, LITTLE_ENDIAN);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT, LITTLE_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT, 0, LITTLE_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{-1,2,-3,4}, LITTLE_ENDIAN);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT, LITTLE_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT, 0, LITTLE_ENDIAN), 0.001f);
    }

    @Test
    public void parseFloat_BigEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{4,3,2,1}, BIG_ENDIAN);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT, BIG_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(1.97120998E9f, parser.getFloatValue(FORMAT_FLOAT, 0, BIG_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{-4, 3, -2, 1}, BIG_ENDIAN);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT, BIG_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(26.1633, parser.getFloatValue(FORMAT_FLOAT, 0, BIG_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{4, -3, 2, -1}, BIG_ENDIAN);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT, BIG_ENDIAN), 0.001f);

        parser.setOffset(0);
        assertEquals(-1.958409984E9f, parser.getFloatValue(FORMAT_FLOAT, 0, BIG_ENDIAN), 0.001f);
    }

    @Test
    public void parseSFloat_LittleEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{1,2}, LITTLE_ENDIAN);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT, LITTLE_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT, 0, LITTLE_ENDIAN), 0.0000001f);

        parser = new BluetoothBytesParser(new byte[]{1,-2}, LITTLE_ENDIAN);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT), 0.0000001f);

        parser.setOffset(0);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT, LITTLE_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT, 0, LITTLE_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{-1,2}, LITTLE_ENDIAN);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT), 0.0000001f);

        parser.setOffset(0);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT, LITTLE_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT, 0, LITTLE_ENDIAN), 0.0000001f);
    }

    @Test
    public void parseSFloat_BigEndianTest() {
        BluetoothBytesParser parser;

        parser = new BluetoothBytesParser(new byte[]{2,1}, BIG_ENDIAN);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT), 0.001f);

        parser.setOffset(0);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT, BIG_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(513.0f, parser.getFloatValue(FORMAT_SFLOAT, 0, BIG_ENDIAN), 0.0000001f);

        parser = new BluetoothBytesParser(new byte[]{-2,1}, BIG_ENDIAN);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT), 0.0000001f);

        parser.setOffset(0);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT, BIG_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(-51.099998474121094f, parser.getFloatValue(FORMAT_SFLOAT, 0, BIG_ENDIAN), 0.001f);

        parser = new BluetoothBytesParser(new byte[]{2, -1}, BIG_ENDIAN);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT), 0.0000001f);

        parser.setOffset(0);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT, BIG_ENDIAN), 0.0000001f);

        parser.setOffset(0);
        assertEquals(767.0f, parser.getFloatValue(FORMAT_SFLOAT, 0, BIG_ENDIAN), 0.0000001f);
    }

    @Test
    public void parseStringTest() {
        BluetoothBytesParser parser;
        String testString = "Hallo";
        String testString2 = "Hallo\0\0";
        String testString3 = "Hallo\0 \0 ";

        parser = new BluetoothBytesParser(testString.getBytes());
        assertEquals(testString, parser.getStringValue());

        // Test cleaning up trailing zero bytes
        parser = new BluetoothBytesParser(testString2.getBytes());
        assertEquals(testString, parser.getStringValue());

        // Test cleaning up trailing zero bytes and spaces
        parser = new BluetoothBytesParser(testString3.getBytes());
        assertEquals(testString, parser.getStringValue());
    }

    @Test
    public void parseDateTimeTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(new byte[]{(byte)0xE4, 0x07, 0x01, 0x02, 0x0a, 0x15, 0x30});
        Date dateTime = parser.getDateTime();

        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(dateTime);

        assertEquals(2020, calendar.get(GregorianCalendar.YEAR));
        assertEquals(1, calendar.get(GregorianCalendar.MONTH) + 1);
        assertEquals(2, calendar.get(GregorianCalendar.DAY_OF_MONTH));
        assertEquals(10, calendar.get(GregorianCalendar.HOUR_OF_DAY));
        assertEquals(21, calendar.get(GregorianCalendar.MINUTE));
        assertEquals(48, calendar.get(GregorianCalendar.SECOND));
    }

    @Test
    public void setStringTest() {
        String testString = "Hallo";
        String testString2 = " Martijn";
        BluetoothBytesParser parser = new BluetoothBytesParser();

        // Set string in empty byte array
        parser.setString(testString);
        assertEquals(testString, parser.getStringValue(0));

        // Set string after previous string
        parser.setString(testString2);
        assertEquals(testString+testString2, parser.getStringValue(0));

        // Set string somewhere in the middle
        parser.setString("e", 1);
        assertEquals("Hello Martijn", parser.getStringValue(0));
    }

    @Test
    public void setUINT8_Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        parser.setIntValue(123, FORMAT_UINT8);
        assertEquals(123, (int) parser.getIntValue(FORMAT_UINT8, 0, LITTLE_ENDIAN));
    }

    @Test
    public void setSINT8_Test() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        parser.setIntValue(-123, FORMAT_SINT8);
        assertEquals(-123, (int) parser.getIntValue(FORMAT_SINT8, 0, LITTLE_ENDIAN));
    }

    @Test
    public void setUINT16_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(1234, FORMAT_UINT16);
        assertEquals(1234, (int) parser.getIntValue(FORMAT_UINT16, 0, LITTLE_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(1234, FORMAT_UINT16);
        parser.setIntValue(5678, FORMAT_UINT16);
        assertEquals(4, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(1234, (int)parser.getIntValue(FORMAT_UINT16));
        assertEquals(5678, (int)parser.getIntValue(FORMAT_UINT16));
    }

    @Test
    public void setUINT16_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(1234, FORMAT_UINT16);
        assertEquals(1234, (int) parser.getIntValue(FORMAT_UINT16, 0, BIG_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(1234, FORMAT_UINT16);
        parser.setIntValue(5678, FORMAT_UINT16);
        assertEquals(4, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(1234, (int)parser.getIntValue(FORMAT_UINT16));
        assertEquals(5678, (int)parser.getIntValue(FORMAT_UINT16));
    }

    @Test
    public void setSINT16_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(-1234, FORMAT_SINT16);
        assertEquals(-1234, (int) parser.getIntValue(FORMAT_SINT16, 0, LITTLE_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(-1234, FORMAT_SINT16);
        parser.setIntValue(5678, FORMAT_SINT16);
        assertEquals(4, parser.getValue().length);

        parser.setOffset(0);
        assertEquals(-1234, (int)parser.getIntValue(FORMAT_SINT16));
        assertEquals(5678, (int)parser.getIntValue(FORMAT_SINT16));
    }

    @Test
    public void setSINT16_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(-1234, FORMAT_SINT16);
        assertEquals(-1234, (int) parser.getIntValue(FORMAT_SINT16, 0, BIG_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(-1234, FORMAT_SINT16);
        parser.setIntValue(5678, FORMAT_SINT16);
        assertEquals(4, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(-1234, (int)parser.getIntValue(FORMAT_SINT16));
        assertEquals(5678, (int)parser.getIntValue(FORMAT_SINT16));
    }

    @Test
    public void setUINT32_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(1234567890, FORMAT_UINT32);
        assertEquals(1234567890, (int) parser.getIntValue(FORMAT_UINT32, 0, LITTLE_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(1234567890, FORMAT_UINT32);
        parser.setIntValue(567890123, FORMAT_UINT32);
        assertEquals(8, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(1234567890, (int)parser.getIntValue(FORMAT_UINT32));
        assertEquals(567890123, (int)parser.getIntValue(FORMAT_UINT32));
    }

    @Test
    public void setUINT32_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(1234567890, FORMAT_UINT32);
        assertEquals(1234567890, (int) parser.getIntValue(FORMAT_UINT32, 0, BIG_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(1234567890, FORMAT_UINT32);
        parser.setIntValue(567890123, FORMAT_UINT32);
        assertEquals(8, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(1234567890, (int)parser.getIntValue(FORMAT_UINT32));
        assertEquals(567890123, (int)parser.getIntValue(FORMAT_UINT32));
    }

    @Test
    public void setSINT32_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(-1234567890, FORMAT_SINT32);
        assertEquals(-1234567890, (int) parser.getIntValue(FORMAT_SINT32, 0, LITTLE_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(-1234567890, FORMAT_SINT32);
        parser.setIntValue(567890123, FORMAT_SINT32);
        assertEquals(8, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(-1234567890, (int)parser.getIntValue(FORMAT_SINT32));
        assertEquals(567890123, (int)parser.getIntValue(FORMAT_SINT32));
    }

    @Test
    public void setSINT32_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(-1234567890, FORMAT_SINT32);
        assertEquals(-1234567890, (int) parser.getIntValue(FORMAT_SINT32, 0, BIG_ENDIAN));

        // Add 2 values after each other
        parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(-1234567890, FORMAT_SINT32);
        parser.setIntValue(567890123, FORMAT_SINT32);
        assertEquals(8, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(-1234567890, (int)parser.getIntValue(FORMAT_SINT32));
        assertEquals(567890123, (int)parser.getIntValue(FORMAT_SINT32));
    }

    @Test
    public void setLong_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setLong(1234567890123L);
        assertEquals(1234567890123L, (long) parser.getLongValue(0, LITTLE_ENDIAN));

        parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setIntValue(123, FORMAT_UINT8);
        parser.setLong(1234567890123L);
        assertEquals(9, parser.getValue().length);
        parser.setOffset(0);
        assertEquals(123, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(1234567890123L, (long) parser.getLongValue(LITTLE_ENDIAN));
    }

    @Test
    public void setLong_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setLong(1234567890123L);
        assertEquals(1234567890123L, (long) parser.getLongValue(0, BIG_ENDIAN));

        parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setIntValue(123, FORMAT_UINT8);
        parser.setLong(1234567890123L);
        assertEquals(9, parser.getValue().length);

        parser.setOffset(0);
        assertEquals(123, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(1234567890123L, (long) parser.getLongValue(BIG_ENDIAN));

        parser.setLong(987654321L);
        parser.setOffset(0);
        assertEquals(123, (int) parser.getIntValue(FORMAT_UINT8));
        assertEquals(1234567890123L, (long) parser.getLongValue());
        assertEquals(987654321L, (long) parser.getLongValue());
    }

    @Test
    public void setSFloat_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setFloatValue(190, -2, FORMAT_SFLOAT, 0);
        assertEquals(1.90f, parser.getFloatValue(FORMAT_SFLOAT));
    }

    @Test
    public void setSFloat_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setFloatValue(190, -2, FORMAT_SFLOAT, 0);
        assertEquals(1.90f, parser.getFloatValue(FORMAT_SFLOAT));
    }

    @Test
    public void setFloat_LittleEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setFloatValue(190, -2, FORMAT_FLOAT, 0);
        assertEquals(1.90f, parser.getFloatValue(FORMAT_FLOAT));

        parser = new BluetoothBytesParser();
        parser.setFloatValue(5.3f, 1);
        parser.setFloatValue(36.86f, 2);

        parser.setOffset(0);
        assertEquals(5.3f, parser.getFloatValue(FORMAT_FLOAT));
        assertEquals(36.86f, parser.getFloatValue(FORMAT_FLOAT));
    }

    @Test
    public void setFloat_BigEndianTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(BIG_ENDIAN);
        parser.setFloatValue(190, -2, FORMAT_FLOAT, 0);
        assertEquals(1.90f, parser.getFloatValue(FORMAT_FLOAT));

        parser = new BluetoothBytesParser();
        parser.setFloatValue(5.3f, 1);
        parser.setOffset(0);
        assertEquals(5.3f, parser.getFloatValue(FORMAT_FLOAT));
    }

    @Test
    public void setValueTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser(LITTLE_ENDIAN);
        parser.setValue(new byte[]{(byte) 0xE4, 0x07});
        assertEquals(2, parser.getValue().length);
        assertEquals(2020, (int) parser.getIntValue(FORMAT_UINT16));
    }

    @Test
    public void setDateTimeTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        Calendar calendar = Calendar.getInstance();
        long timestamp = 1578310812000L;
        calendar.setTimeInMillis(timestamp);

        parser.setDateTime(calendar);
        parser.setOffset(0);
        Date parsedDateTime = parser.getDateTime();
        assertEquals(timestamp, parsedDateTime.getTime());
    }

    @Test
    public void setCurrentTimeTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        Calendar calendar = Calendar.getInstance();
        long timestamp = 1578310812000L;
        calendar.setTimeInMillis(timestamp);

        parser.setCurrentTime(calendar);
        parser.setOffset(0);
        Date parsedDateTime = parser.getDateTime();
        assertEquals(timestamp, parsedDateTime.getTime());
        assertEquals(10, parser.getValue().length);
        calendar.setTime(parsedDateTime);
    }

    @Test
    public void mergeArraysTest() {
        byte[] array1 = new byte[]{1,2,3};
        byte[] array2 = new byte[]{4,5,6};

        byte[] mergedArrays = BluetoothBytesParser.mergeArrays(array1, array2);
        assertEquals(6, mergedArrays.length);
        assertEquals(array1[0], mergedArrays[0]);
        assertEquals(array1[1], mergedArrays[1]);
        assertEquals(array1[2], mergedArrays[2]);
        assertEquals(array2[0], mergedArrays[3]);
        assertEquals(array2[1], mergedArrays[4]);
        assertEquals(array2[2], mergedArrays[5]);
    }

    @Test
    public void toStringTest() {
        BluetoothBytesParser parser = new BluetoothBytesParser();
        parser.setValue(new byte[]{0x12, 0x34, 0x56});
        String string = parser.toString();
        assertEquals("123456", string);
    }

    @Test
    public void hexString_to_byte_array_Test() {
        byte[] value = new byte[]{0x01, 0x40, (byte) 0x80, (byte) 0x81, (byte)0xA0, (byte)0xF0, (byte) 0xFF};
        String valueString = BluetoothBytesParser.bytes2String(value);
        byte[] decodedValue = BluetoothBytesParser.string2bytes(valueString);
        assertArrayEquals(value, decodedValue);
    }

    @Test
    public void getByteArray_Test() {
        byte[] value = new byte[]{0x01, 0x40, (byte) 0x80, (byte) 0x81, (byte)0xA0, (byte)0xF0, (byte) 0xFF};
        BluetoothBytesParser parser = new BluetoothBytesParser(value);
        byte[] byteArray = parser.getByteArray(3);

        assertArrayEquals(new byte[]{0x01, 0x40, (byte) 0x80}, byteArray);
        assertEquals(3, parser.getOffset() );
    }
}
