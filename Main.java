import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

// ========== 1. ABSTRACTION ==========
abstract class Vehicle {
    private final String plate, owner;

    Vehicle(String plate, String owner) {
        this.plate = plate.trim().toUpperCase();
        this.owner = owner.trim();
    }

    String getPlate() { return plate; }
    String getOwner() { return owner; }
    abstract String getType();
    abstract double getRate();   // rate per hour
}

// ========== 2. INHERITANCE + POLYMORPHISM ==========
class Bike extends Vehicle {
    Bike(String p, String o) { super(p, o); }
    String getType() { return "BIKE"; }
    double getRate() { return 20; }
}

class Car extends Vehicle {
    Car(String p, String o) { super(p, o); }
    String getType() { return "CAR"; }
    double getRate() { return 50; }
}

class Truck extends Vehicle {
    Truck(String p, String o) { super(p, o); }
    String getType() { return "TRUCK"; }
    double getRate() { return 100; }
}

// ========== 3. PARKING SLOT ==========
class ParkingSlot {
    final int number;
    final String type;
    Vehicle vehicle;   // null = free

    ParkingSlot(int number, String type) {
        this.number = number;
        this.type = type;
    }

    boolean isOccupied() { return vehicle != null; }
}

// ========== 4. CUSTOM EXCEPTION ==========
class ParkingException extends RuntimeException {
    ParkingException(String msg) { super(msg); }
}

// ========== 5. TICKET (Date & Time API) ==========
class ParkingTicket {
    private static int counter = 1000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    final int id = ++counter;
    final Vehicle vehicle;
    final int slotNumber;
    final LocalDateTime entryTime = LocalDateTime.now();
    LocalDateTime exitTime;
    double fee;

    ParkingTicket(Vehicle vehicle, int slotNumber) {
        this.vehicle = vehicle;
        this.slotNumber = slotNumber;
    }

    void close() {
        exitTime = LocalDateTime.now();
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        long hours = Math.max(1, (minutes + 59) / 60);   // round up, minimum 1 hour
        fee = hours * vehicle.getRate();
    }

    String entry() { return entryTime.format(FMT); }

    @Override
    public String toString() {
        return "===== SMARTPARK TICKET =====\n"
            + "Ticket ID : " + id + "\n"
            + "Vehicle   : " + vehicle.getType() + "\n"
            + "Plate     : " + vehicle.getPlate() + "\n"
            + "Owner     : " + vehicle.getOwner() + "\n"
            + "Slot      : " + slotNumber + "\n"
            + "Entry     : " + entry() + "\n"
            + "Exit      : " + (exitTime == null ? "-" : exitTime.format(FMT)) + "\n"
            + "Fee       : Rs. " + String.format("%.2f", fee);
    }
}

// ========== 6. INTERFACE ==========
interface ParkingOperations {
    ParkingTicket parkVehicle(Vehicle v);
    ParkingTicket removeVehicle(String plate);
    ParkingTicket searchVehicle(String plate);
}

// ========== 7. BUSINESS LOGIC (Collections) ==========
class ParkingLot implements ParkingOperations {
    final ArrayList<ParkingSlot> slots = new ArrayList<>();
    final HashMap<String, ParkingTicket> active = new HashMap<>();
    final ArrayList<ParkingTicket> completed = new ArrayList<>();
    double revenue = 0;

    ParkingLot() {
        addSlot("BIKE"); addSlot("BIKE");
        addSlot("CAR");  addSlot("CAR");
        addSlot("TRUCK");
    }

    void addSlot(String type) {
        slots.add(new ParkingSlot(slots.size() + 1, type));
    }

    public ParkingTicket parkVehicle(Vehicle v) {
        if (active.containsKey(v.getPlate()))
            throw new ParkingException("This vehicle is already parked!");

        for (ParkingSlot s : slots) {   // first (nearest) free slot of matching type
            if (!s.isOccupied() && s.type.equals(v.getType())) {
                s.vehicle = v;
                ParkingTicket t = new ParkingTicket(v, s.number);
                active.put(v.getPlate(), t);
                return t;
            }
        }
        throw new ParkingException("No free slot for " + v.getType());
    }

    public ParkingTicket removeVehicle(String plate) {
        ParkingTicket t = searchVehicle(plate);
        if (t == null) throw new ParkingException("Vehicle not found!");

        t.close();
        slots.get(t.slotNumber - 1).vehicle = null;
        active.remove(t.vehicle.getPlate());
        completed.add(t);
        revenue += t.fee;
        return t;
    }

    public ParkingTicket searchVehicle(String plate) {
        return active.get(plate.trim().toUpperCase());
    }

    int freeSlots() { return slots.size() - active.size(); }
}

// ========== 8. SIMPLE SWING UI ==========
public class Main extends JFrame {
    private final ParkingLot lot = new ParkingLot();
    private final JLabel stats = new JLabel();
    private final JLabel revenueLabel = new JLabel();
    private final DefaultTableModel slotModel =
        new DefaultTableModel(new String[]{"Slot", "Type", "Status", "Plate"}, 0);
    private final DefaultTableModel revenueModel =
        new DefaultTableModel(new String[]{"Ticket", "Plate", "Type", "Slot", "Fee"}, 0);

    public Main() {
        super("SmartPark - Parking Management System");

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Entry", entryTab());
        tabs.addTab("Exit", exitTab());
        tabs.addTab("Search", searchTab());
        tabs.addTab("Slots", tablePanel(slotModel, stats));
        tabs.addTab("Revenue", tablePanel(revenueModel, revenueLabel));
        tabs.addTab("Admin", adminTab());
        tabs.addChangeListener(e -> refresh());

        add(tabs);
        refresh();
        setSize(650, 420);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ---- Entry ----
    private JPanel entryTab() {
        JTextField plate = new JTextField(), owner = new JTextField();
        JComboBox<String> type = new JComboBox<>(new String[]{"BIKE", "CAR", "TRUCK"});
        JButton btn = new JButton("Allocate Slot");

        btn.addActionListener(e -> run(() -> {
            String p = plate.getText().trim(), o = owner.getText().trim();
            if (p.isEmpty() || o.isEmpty()) throw new ParkingException("Fill all fields!");

            Vehicle v;
            switch ((String) type.getSelectedItem()) {   // polymorphism: one Vehicle reference
                case "BIKE": v = new Bike(p, o); break;
                case "CAR":  v = new Car(p, o);  break;
                default:     v = new Truck(p, o);
            }
            ParkingTicket t = lot.parkVehicle(v);
            JOptionPane.showMessageDialog(this, "Parked!\nSlot: " + t.slotNumber + "\nTicket ID: " + t.id);
            plate.setText(""); owner.setText("");
        }));

        return form(new String[]{"Number Plate", "Owner Name", "Vehicle Type"},
                    new JComponent[]{plate, owner, type}, btn);
    }

    // ---- Exit ----
    private JPanel exitTab() {
        JTextField plate = new JTextField();
        JButton btn = new JButton("Process Exit");

        btn.addActionListener(e -> run(() -> {
            ParkingTicket t = lot.removeVehicle(plate.getText());
            JOptionPane.showMessageDialog(this, new JTextArea(t.toString()), "Ticket", JOptionPane.INFORMATION_MESSAGE);
            plate.setText("");
        }));

        return form(new String[]{"Number Plate"}, new JComponent[]{plate}, btn);
    }

    // ---- Search ----
    private JPanel searchTab() {
        JTextField plate = new JTextField();
        JButton btn = new JButton("Search");

        btn.addActionListener(e -> run(() -> {
            ParkingTicket t = lot.searchVehicle(plate.getText());
            if (t == null) throw new ParkingException("Vehicle not found!");
            JOptionPane.showMessageDialog(this,
                "Owner: " + t.vehicle.getOwner()
                + "\nType: " + t.vehicle.getType()
                + "\nSlot: " + t.slotNumber
                + "\nEntry: " + t.entry());
        }));

        return form(new String[]{"Number Plate"}, new JComponent[]{plate}, btn);
    }

    // ---- Admin ----
    private JPanel adminTab() {
        JComboBox<String> type = new JComboBox<>(new String[]{"BIKE", "CAR", "TRUCK"});
        JButton btn = new JButton("Add Slot");

        btn.addActionListener(e -> run(() -> {
            lot.addSlot((String) type.getSelectedItem());
            JOptionPane.showMessageDialog(this, "Slot added!");
        }));

        return form(new String[]{"Slot Type"}, new JComponent[]{type}, btn);
    }

    // ---- Helpers ----
    private JPanel form(String[] labels, JComponent[] fields, JButton button) {
        JPanel grid = new JPanel(new GridLayout(labels.length + 1, 2, 10, 15));
        for (int i = 0; i < labels.length; i++) {
            grid.add(new JLabel(labels[i]));
            grid.add(fields[i]);
        }
        grid.add(new JLabel());
        grid.add(button);

        JPanel page = new JPanel(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        page.add(grid, BorderLayout.NORTH);
        return page;
    }

    private JPanel tablePanel(DefaultTableModel model, JLabel footer) {
        JPanel page = new JPanel(new BorderLayout(5, 5));
        page.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    // Runs an action and shows any ParkingException as an error dialog
    private void run(Runnable action) {
        try {
            action.run();
            refresh();
        } catch (ParkingException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refresh() {
        stats.setText("  Total: " + lot.slots.size() + "   Available: " + lot.freeSlots()
                    + "   Occupied: " + lot.active.size());
        revenueLabel.setText("  Total Revenue: Rs. " + String.format("%.2f", lot.revenue));

        slotModel.setRowCount(0);
        for (ParkingSlot s : lot.slots) {
            slotModel.addRow(new Object[]{s.number, s.type,
                s.isOccupied() ? "OCCUPIED" : "AVAILABLE",
                s.isOccupied() ? s.vehicle.getPlate() : "-"});
        }

        revenueModel.setRowCount(0);
        for (ParkingTicket t : lot.completed) {
            revenueModel.addRow(new Object[]{t.id, t.vehicle.getPlate(), t.vehicle.getType(),
                t.slotNumber, "Rs. " + String.format("%.2f", t.fee)});
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
