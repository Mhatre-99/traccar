package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.traccar.QuadConsumer;
import org.traccar.helper.BitUtil;
import org.traccar.helper.DataConverter;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AtrackProtocolDecoderDataReader {

    private final Map<String, QuadConsumer<Position, CellTower, String[], Integer>> keyProcessors;

    public AtrackProtocolDecoderDataReader() {
        keyProcessors = new HashMap<>();
        initializeKeyProcessors();
    }

    public String readString(ByteBuf buf) {
        String result = null;
        int index = buf.indexOf(buf.readerIndex(), buf.writerIndex(), (byte) 0);
        if (index > buf.readerIndex()) {
            result = buf.readSlice(index - buf.readerIndex()).toString(StandardCharsets.US_ASCII);
        }
        buf.readByte();
        return result;
    }

    public void decodeBeaconData(Position position, int mode, int mask, ByteBuf data) {
        int i = 1;
        while (data.isReadable()) {
            if (BitUtil.check(mask, 7)) {
                position.set("tag" + i + "Id", ByteBufUtil.hexDump(data.readSlice(6)));
            }
            switch (mode) {
                case 1:
                    decodeModeOne(position, data, i, mask);
                    break;
                case 2:
                    decodeModeTwo(position, data, i, mask);
                    break;
                case 3:
                    decodeModeThree(position, data, i, mask);
                    break;
                case 4:
                    decodeModeFour(position, data, i, mask);
                    break;
                default:
                    break;
            }
            i += 1;
        }
    }

    public void decodeModeOne(Position position, ByteBuf data, int i, int mask) {
        if (BitUtil.check(mask, 6)) {
            data.readUnsignedShort(); // major
        }
        if (BitUtil.check(mask, 5)) {
            data.readUnsignedShort(); // minor
        }
        if (BitUtil.check(mask, 4)) {
            data.readUnsignedByte(); // tx power
        }
        if (BitUtil.check(mask, 3)) {
            position.set("tag" + i + "Rssi", data.readUnsignedByte());
        }
    }

    public void decodeModeTwo(Position position, ByteBuf data, int i, int mask) {
        if (BitUtil.check(mask, 6)) {
            data.readUnsignedShort(); // battery voltage
        }
        if (BitUtil.check(mask, 5)) {
            position.set("tag" + i + "Temp", data.readUnsignedShort());
        }
        if (BitUtil.check(mask, 4)) {
            data.readUnsignedByte(); // tx power
        }
        if (BitUtil.check(mask, 3)) {
            position.set("tag" + i + "Rssi", data.readUnsignedByte());
        }
    }

    public void decodeModeThree(Position position, ByteBuf data, int i, int mask) {
        if (BitUtil.check(mask, 6)) {
            position.set("tag" + i + "Humidity", data.readUnsignedShort());
        }
        if (BitUtil.check(mask, 5)) {
            position.set("tag" + i + "Temp", data.readUnsignedShort());
        }
        if (BitUtil.check(mask, 3)) {
            position.set("tag" + i + "Rssi", data.readUnsignedByte());
        }
        if (BitUtil.check(mask, 2)) {
            data.readUnsignedShort();
        }
    }

    public void decodeModeFour(Position position, ByteBuf data, int i, int mask) {
        if (BitUtil.check(mask, 6)) {
            int hardwareId = data.readUnsignedByte();
            if (BitUtil.check(mask, 5)) {
                switch (hardwareId) {
                    case 1:
                    case 4:
                        data.skipBytes(11); // fuel
                        break;
                    case 2:
                        data.skipBytes(2); // temperature
                        break;
                    case 3:
                        data.skipBytes(6); // temperature and luminosity
                        break;
                    case 5:
                        data.skipBytes(10); // temperature, humidity, luminosity and pressure
                        break;
                    default:
                        break;
                }
            }
        }
        if (BitUtil.check(mask, 4)) {
            data.skipBytes(9); // name
        }
    }

    private void processSA(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_SATELLITES, Integer.parseInt(values[i]));
    }

    private void processBV(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_BATTERY, Integer.parseInt(values[i]) * 0.1);
    }

    private void processMV(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_POWER, Integer.parseInt(values[i]) * 0.1);
    }

    private void processGQ(Position position, CellTower cellTower, String[] values, int i) {
        cellTower.setSignalStrength(Integer.parseInt(values[i]));
    }

    private void processCE(Position position, CellTower cellTower, String[] values, int i) {
        cellTower.setCellId(Long.parseLong(values[i]));
    }

    private void processLC(Position position, CellTower cellTower, String[] values, int i) {
        cellTower.setLocationAreaCode(Integer.parseInt(values[i]));
    }

    private void processCN(Position position, CellTower cellTower, String[] values, int i) {
        if (values[i].length() > 3) {
            cellTower.setMobileCountryCode(Integer.parseInt(values[i].substring(0, 3)));
            cellTower.setMobileNetworkCode(Integer.parseInt(values[i].substring(3)));
        }
    }

    private void processPC(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.PREFIX_COUNT + 1, Integer.parseInt(values[i]));
    }

    private void processAT(Position position, CellTower cellTower, String[] values, int i) {
        position.setAltitude(Integer.parseInt(values[i]));
    }

    private void processRP(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_RPM, Integer.parseInt(values[i]));
    }

    private void processGS(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_RSSI, Integer.parseInt(values[i]));
    }

    private void processDT(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_ARCHIVE, Integer.parseInt(values[i]) == 1);
    }

    private void processVN(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_VIN, values[i]);
    }

    private void processTR(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_THROTTLE, Integer.parseInt(values[i]));
    }

    private void processET(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_COOLANT_TEMP, Integer.parseInt(values[i]));
    }

    private void processFL(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_FUEL_LEVEL, Integer.parseInt(values[i]));
    }

    private void processFC(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_FUEL_CONSUMPTION, Integer.parseInt(values[i]));
    }

    private void processAV1(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.PREFIX_ADC + 1, Integer.parseInt(values[i]));
    }

    private void processCD(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_ICCID, values[i]);
    }

    private void processEH(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_HOURS, UnitsConverter.msFromHours(Integer.parseInt(values[i]) * 0.1));
    }

    private void processIA(Position position, CellTower cellTower, String[] values, int i) {
        position.set("intakeTemp", Integer.parseInt(values[i]));
    }

    private void processEL(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_ENGINE_LOAD, Integer.parseInt(values[i]));
    }

    private void processHA(Position position, CellTower cellTower, String[] values, int i) {
        if (Integer.parseInt(values[i]) > 0) {
            position.set(Position.KEY_ALARM, Position.ALARM_ACCELERATION);
        }
    }

    private void processHB(Position position, CellTower cellTower, String[] values, int i) {
        if (Integer.parseInt(values[i]) > 0) {
            position.set(Position.KEY_ALARM, Position.ALARM_BRAKING);
        }
    }

    private void processHC(Position position, CellTower cellTower, String[] values, int i) {
        if (Integer.parseInt(values[i]) > 0) {
            position.set(Position.KEY_ALARM, Position.ALARM_CORNERING);
        }
    }

    private void processMT(Position position, CellTower cellTower, String[] values, int i) {
        position.set(Position.KEY_MOTION, Integer.parseInt(values[i]) > 0);
    }

    private void processBC(Position position, CellTower cellTower, String[] values, int i) {
        String[] beaconValues = values[i].split(":");
        decodeBeaconData(
                position, Integer.parseInt(beaconValues[0]), Integer.parseInt(beaconValues[1]),
                Unpooled.wrappedBuffer(DataConverter.parseHex(beaconValues[2])));
    }

    private void processUnknownKey(Position position, CellTower cellTower, String[] values, int i) {

    }

    private void initializeKeyProcessors() {
        keyProcessors.put("SA", this::processSA);
        keyProcessors.put("BV", this::processBV);
        keyProcessors.put("MV", this::processMV);
        keyProcessors.put("GQ", this::processGQ);
        keyProcessors.put("CE", this::processCE);
        keyProcessors.put("LC", this::processLC);
        keyProcessors.put("CN", this::processCN);
        keyProcessors.put("PC", this::processPC);
        keyProcessors.put("AT", this::processAT);
        keyProcessors.put("RP", this::processRP);
        keyProcessors.put("GS", this::processGS);
        keyProcessors.put("DT", this::processDT);
        keyProcessors.put("VN", this::processVN);
        keyProcessors.put("TR", this::processTR);
        keyProcessors.put("ET", this::processET);
        keyProcessors.put("FL", this::processFL);
        keyProcessors.put("FC", this::processFC);
        keyProcessors.put("AV1", this::processAV1);
        keyProcessors.put("CD", this::processCD);
        keyProcessors.put("EH", this::processEH);
        keyProcessors.put("IA", this::processIA);
        keyProcessors.put("EL", this::processEL);
        keyProcessors.put("HA", this::processHA);
        keyProcessors.put("HB", this::processHB);
        keyProcessors.put("HC", this::processHC);
        keyProcessors.put("MT", this::processMT);
        keyProcessors.put("BC", this::processBC);
    }


    public void readTextCustomData(Position position, String data, String form) {
        CellTower cellTower = new CellTower();
        String[] keys = form.substring(1).split("%");
        String[] values = data.split(",|\r\n");

        for (int i = 0; i < Math.min(keys.length, values.length); i++) {
            keyProcessors.getOrDefault(keys[i], this::processUnknownKey).accept(position, cellTower, values, i);
        }

        if (cellTower.getMobileCountryCode() != null
                && cellTower.getMobileNetworkCode() != null
                && cellTower.getCellId() != null
                && cellTower.getLocationAreaCode() != null) {
            position.setNetwork(new Network(cellTower));
        } else if (cellTower.getSignalStrength() != null) {
            position.set(Position.KEY_RSSI, cellTower.getSignalStrength());
        }
    }

    public void readBinaryCustomData(Position position, ByteBuf buf, String form) {
        CellTower cellTower = new CellTower();
        String[] keys = form.substring(1).split("%");
        for (String key : keys) {
            switch (key) {
                case "SA":
                    position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                    break;
                case "MV":
                    position.set(Position.KEY_POWER, buf.readUnsignedShort() * 0.1);
                    break;
                case "BV":
                    position.set(Position.KEY_BATTERY, buf.readUnsignedShort() * 0.1);
                    break;
                case "GQ":
                    cellTower.setSignalStrength((int) buf.readUnsignedByte());
                    break;
                case "CE":
                    cellTower.setCellId(buf.readUnsignedInt());
                    break;
                case "LC":
                    cellTower.setLocationAreaCode(buf.readUnsignedShort());
                    break;
                case "CN":
                    int combinedMobileCodes = (int) (buf.readUnsignedInt() % 100000); // cccnn
                    cellTower.setMobileCountryCode(combinedMobileCodes / 100);
                    cellTower.setMobileNetworkCode(combinedMobileCodes % 100);
                    break;
                case "RL":
                    buf.readUnsignedByte(); // rxlev
                    break;
                case "PC":
                    position.set(Position.PREFIX_COUNT + 1, buf.readUnsignedInt());
                    break;
                case "AT":
                    position.setAltitude(buf.readUnsignedInt());
                    break;
                case "RP":
                    position.set(Position.KEY_RPM, buf.readUnsignedShort());
                    break;
                case "GS":
                    position.set(Position.KEY_RSSI, buf.readUnsignedByte());
                    break;
                case "DT":
                    position.set(Position.KEY_ARCHIVE, buf.readUnsignedByte() == 1);
                    break;
                case "VN":
                    position.set(Position.KEY_VIN, readString(buf));
                    break;
                case "MF":
                    buf.readUnsignedShort(); // mass air flow rate
                    break;
                case "EL":
                    buf.readUnsignedByte(); // engine load
                    break;
                case "TR":
                    position.set(Position.KEY_THROTTLE, buf.readUnsignedByte());
                    break;
                case "ET":
                    position.set(Position.PREFIX_TEMP + 1, buf.readUnsignedShort());
                    break;
                case "FL":
                    position.set(Position.KEY_FUEL_LEVEL, buf.readUnsignedByte());
                    break;
                case "ML":
                    buf.readUnsignedByte(); // mil status
                    break;
                case "FC":
                    position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedInt());
                    break;
                case "CI":
                    readString(buf); // format string
                    break;
                case "AV1":
                    position.set(Position.PREFIX_ADC + 1, buf.readUnsignedShort());
                    break;
                case "NC":
                    readString(buf); // gsm neighbor cell info
                    break;
                case "SM":
                    buf.readUnsignedShort(); // max speed between reports
                    break;
                case "GL":
                    readString(buf); // google link
                    break;
                case "MA":
                    readString(buf); // mac address
                    break;
                case "PD":
                    buf.readUnsignedByte(); // pending code status
                    break;
                case "CD":
                    position.set(Position.KEY_ICCID, readString(buf));
                    break;
                case "CM":
                    buf.readLong(); // imsi
                    break;
                case "GN":
                    buf.skipBytes(60); // g sensor data
                    break;
                case "GV":
                    buf.skipBytes(6); // maximum g force
                    break;
                case "ME":
                    buf.readLong(); // imei
                    break;
                case "IA":
                    buf.readUnsignedByte(); // intake air temperature
                    break;
                case "MP":
                    buf.readUnsignedByte(); // manifold absolute pressure
                    break;
                case "EO":
                    position.set(Position.KEY_ODOMETER, UnitsConverter.metersFromMiles(buf.readUnsignedInt()));
                    break;
                case "EH":
                    position.set(Position.KEY_HOURS, buf.readUnsignedInt() * 360000);
                    break;
                case "ZO1":
                    buf.readUnsignedByte(); // brake stroke status
                    break;
                case "ZO2":
                    buf.readUnsignedByte(); // warning indicator status
                    break;
                case "ZO3":
                    buf.readUnsignedByte(); // abs control status
                    break;
                case "ZO4":
                    position.set(Position.KEY_THROTTLE, buf.readUnsignedByte() * 0.4);
                    break;
                case "ZO5":
                    buf.readUnsignedByte(); // parking brake status
                    break;
                case "ZO6":
                    position.set(Position.KEY_OBD_SPEED, buf.readUnsignedByte() * 0.805);
                    break;
                case "ZO7":
                    buf.readUnsignedByte(); // cruise control status
                    break;
                case "ZO8":
                    buf.readUnsignedByte(); // accelector pedal position
                    break;
                case "ZO9":
                    position.set(Position.KEY_ENGINE_LOAD, buf.readUnsignedByte() * 0.5);
                    break;
                case "ZO10":
                    position.set(Position.KEY_FUEL_LEVEL, buf.readUnsignedByte() * 0.5);
                    break;
                case "ZO11":
                    buf.readUnsignedByte(); // engine oil pressure
                    break;
                case "ZO12":
                    buf.readUnsignedByte(); // boost pressure
                    break;
                case "ZO13":
                    buf.readUnsignedByte(); // intake temperature
                    break;
                case "ZO14":
                    position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedByte());
                    break;
                case "ZO15":
                    buf.readUnsignedByte(); // brake application pressure
                    break;
                case "ZO16":
                    buf.readUnsignedByte(); // brake primary pressure
                    break;
                case "ZO17":
                    buf.readUnsignedByte(); // brake secondary pressure
                    break;
                case "ZH1":
                    buf.readUnsignedShort(); // cargo weight
                    break;
                case "ZH2":
                    position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort() * 16.428 / 3600);
                    break;
                case "ZH3":
                    position.set(Position.KEY_RPM, buf.readUnsignedShort() * 0.25);
                    break;
                case "ZL1":
                    buf.readUnsignedInt(); // fuel used (natural gas)
                    break;
                case "ZL2":
                    position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 161);
                    break;
                case "ZL3":
                    buf.readUnsignedInt(); // vehicle hours
                    break;
                case "ZL4":
                    position.set(Position.KEY_HOURS, buf.readUnsignedInt() * 5 * 36000);
                    break;
                case "ZS1":
                    position.set(Position.KEY_VIN, readString(buf));
                    break;
                case "JO1":
                    buf.readUnsignedByte(); // pedals
                    break;
                case "JO2":
                    buf.readUnsignedByte(); // power takeoff device
                    break;
                case "JO3":
                    buf.readUnsignedByte(); // accelector pedal position
                    break;
                case "JO4":
                    position.set(Position.KEY_ENGINE_LOAD, buf.readUnsignedByte());
                    break;
                case "JO5":
                    position.set(Position.KEY_FUEL_LEVEL, buf.readUnsignedByte() * 0.4);
                    break;
                case "JO6":
                    buf.readUnsignedByte(); // fms vehicle interface
                    break;
                case "JO7":
                    buf.readUnsignedByte(); // driver 2
                    break;
                case "JO8":
                    buf.readUnsignedByte(); // driver 1
                    break;
                case "JO9":
                    buf.readUnsignedByte(); // drivers
                    break;
                case "JO10":
                    buf.readUnsignedByte(); // system information
                    break;
                case "JO11":
                    position.set(Position.KEY_COOLANT_TEMP, buf.readUnsignedByte() - 40);
                    break;
                case "JO12":
                    buf.readUnsignedByte(); // pto engaged
                    break;
                case "JH1":
                    position.set(Position.KEY_OBD_SPEED, buf.readUnsignedShort() / 256.0);
                    break;
                case "JH2":
                    position.set(Position.KEY_RPM, buf.readUnsignedShort() * 0.125);
                    break;
                case "JH3":
                case "JH4":
                case "JH5":
                case "JH6":
                case "JH7":
                    int index = Integer.parseInt(key.substring(2)) - 2;
                    position.set("axleWeight" + index, buf.readUnsignedShort() * 0.5);
                    break;
                case "JH8":
                    position.set(Position.KEY_ODOMETER_SERVICE, buf.readUnsignedShort() * 5);
                    break;
                case "JH9":
                    buf.readUnsignedShort(); // tachograph speed
                    break;
                case "JH10":
                    buf.readUnsignedShort(); // ambient air temperature
                    break;
                case "JH11":
                    position.set(Position.KEY_FUEL_CONSUMPTION, buf.readUnsignedShort() * 0.05);
                    break;
                case "JH12":
                    buf.readUnsignedShort(); // fuel economy
                    break;
                case "JL1":
                    position.set(Position.KEY_FUEL_USED, buf.readUnsignedInt() * 0.5);
                    break;
                case "JL2":
                    position.set(Position.KEY_HOURS, buf.readUnsignedInt() * 5 * 36000);
                    break;
                case "JL3":
                    position.set(Position.KEY_ODOMETER, buf.readUnsignedInt() * 1000);
                    break;
                case "JL4":
                    position.set(Position.KEY_FUEL_USED, buf.readUnsignedInt() * 0.001);
                    break;
                case "JS1":
                    position.set(Position.KEY_VIN, readString(buf));
                    break;
                case "JS2":
                    readString(buf); // fms version supported
                    break;
                case "JS3":
                    position.set("driver1", readString(buf));
                    break;
                case "JS4":
                    position.set("driver2", readString(buf));
                    break;
                case "JN1":
                    buf.readUnsignedInt(); // cruise control distance
                    break;
                case "JN2":
                    buf.readUnsignedInt(); // excessive idling time
                    break;
                case "JN3":
                    buf.readUnsignedInt(); // excessive idling fuel
                    break;
                case "JN4":
                    buf.readUnsignedInt(); // pto time
                    break;
                case "JN5":
                    buf.readUnsignedInt(); // pto fuel
                    break;
                case "IN0":
                    position.set(Position.KEY_IGNITION, buf.readUnsignedByte() > 0);
                    break;
                case "IN1":
                case "IN2":
                case "IN3":
                    position.set(Position.PREFIX_IN + key.charAt(2), buf.readUnsignedByte() > 0);
                    break;
                case "HA":
                    position.set(Position.KEY_ALARM, buf.readUnsignedByte() > 0 ? Position.ALARM_ACCELERATION : null);
                    break;
                case "HB":
                    position.set(Position.KEY_ALARM, buf.readUnsignedByte() > 0 ? Position.ALARM_BRAKING : null);
                    break;
                case "HC":
                    position.set(Position.KEY_ALARM, buf.readUnsignedByte() > 0 ? Position.ALARM_CORNERING : null);
                    break;
                default:
                    break;
            }
        }

        if (cellTower.getMobileCountryCode() != null
                && cellTower.getMobileNetworkCode() != null
                && cellTower.getCellId() != null && cellTower.getCellId() != 0
                && cellTower.getLocationAreaCode() != null) {
            position.setNetwork(new Network(cellTower));
        } else if (cellTower.getSignalStrength() != null) {
            position.set(Position.KEY_RSSI, cellTower.getSignalStrength());
        }
    }

}
