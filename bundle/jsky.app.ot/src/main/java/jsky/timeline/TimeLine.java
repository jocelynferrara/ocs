package jsky.timeline;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import jsky.science.Time;
import jsky.util.gui.BasicWindowMonitor;
import jsky.util.gui.DialogUtil;
import jsky.coords.HMS;

/**
 * A time line which can contain nodes that can be adjusted by time and duration.
 *
 * <P>This code was developed by NASA, Goddard Space Flight Center, Code 588
 * for the Scientist's Expert Assistant (SEA) project.
 *
 * @version $Revision: 38398 $
 * @author  M. Fishman
 * @author  A. Brighton (modified original version)
 **/
public class TimeLine extends JPanel {

    // constants for setUnitsType()
    public static final String DATE_VIEW = "Date View";
    public static final String TIME_VIEW = "Time View";

    public static final String DISPLAY_WINDOW_CHANGE = "Display Window Change";
    public static final String NODE_ADDED = "node added";
    public static final String NODE_REMOVED = "node removed";

    public static final String SELECTION_MODE = "Selection";
    public static final String ZOOM_MODE = "Zoom";

    protected static final double MIN_DISPLAY_WINDOW = 10.0;
    public static final Cursor DEFAULT_CURSOR = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

    protected Comparator<TimeLineNode> _comparator = new TimeLineNodeComparator();
    protected float _handleHeight = 6;
    protected float _verticalSpacer = 18;
    protected Line2D.Float _centerLine = new Line2D.Float();

    protected Set<TimeLineNode> _nodes;
    protected List<VetoableChangeListener> _vetoableListeners;
    protected int _intervalCount;
    protected String _mode = SELECTION_MODE;
    protected String _unitType = Time.MINUTE;

    protected Time _displayStart;
    protected Time _displayEnd;
    protected Time _intervalInTime;
    protected TimeLineModel _model;

    // If true, draw the timeline start and end labels at the top, otherwise at the bottom
    protected boolean _labelsAtTop = false;

    /**
     *
     * a listener for mouse events.
     *
     **/
    protected MouseAdapter _mouseListener = new MouseAdapter() {

        public void mouseClicked(MouseEvent evt) {
            handleMouseClicked(evt);
        }

        public void mousePressed(MouseEvent evt) {
            handleMousePressed(evt);
        }

        public void mouseReleased(MouseEvent evt) {
            handleMousePressed(evt);
        }

    };

    /**
     *
     * an adapter used to handle mouse drag events
     *
     **/
    protected MouseMotionAdapter _mouseDragListener = new MouseMotionAdapter() {

        public void mouseDragged(MouseEvent evt) {
            // propogate event to nodes
            for (TimeLineNode node : _nodes) {
                node.handleMouseDragEvent(evt);
            }
            repaint();
        }

        public void mouseMoved(MouseEvent evt) {
            // propogate event to nodes
            for (TimeLineNode node : _nodes) {
                node.handleMouseMoveEvent(evt);
            }
            repaint();
        }
    };

    protected KeyAdapter _keyListener = new KeyAdapter() {

        public void keyPressed(KeyEvent evt) {
            handleKeyEvent(evt);
        }

    };

    protected VetoableChangeListener _myChildListener = evt -> {
        validatePropertyChange(evt);
        repaint();
    };

    /**
     * constructor
     *
     **/
    public TimeLine() {
        this(0, 50, 50);
    }


    /**
     *
     * constructor
     *
     * @param interval the number of intervals in a 50 minute time line
     *
     **/
    public TimeLine(int interval) {
        this(0, 50, interval);
    }

    /**
     *
     * constructor
     *
     * @param start the starting minute of the timeline
     * @param end the ending minute of the timeline
     * @param intervals the number of intervals on the timeline
     *
     **/
    public TimeLine(int start, int end, int intervals) {
        this(new Time((double) start, Time.MINUTE), new Time((double) end, Time.MINUTE), intervals);
    }

    /**
     *
     * constructor
     *
     * @param start the starting time of the timeline
     * @param end the ending time of the timeline
     * @param intervals the number of intervals on the timeline
     *
     **/
    public TimeLine(Time start, Time end, int intervals) {
        this(new DefaultTimeLineModel(start, end, intervals));
    }


    /**
     *
     * constructor
     *
     * @param model the time line model
     *
     */
    public TimeLine(TimeLineModel model) {
        super();
        Time start = model.getStartTime();
        Time end = model.getEndTime();
        int intervals = model.getIntervalCount();
        MouseInputListener msListener = new MouseInputAdapter() {

            public void mousePressed(MouseEvent e) {
                setCursor(e);
            }

            public void mouseReleased(MouseEvent e) {
                setCursor(e);
            }

            public void mouseMoved(MouseEvent e) {
                setCursor(e);
            }

            private void setCursor(MouseEvent e) {
                Cursor cursor = DEFAULT_CURSOR;
                for (TimeLineNode node : _nodes) {
                    if (node.containsPoint(e.getPoint())) {
                        cursor = node.getCursor(e);
                        break;
                    }
                }
                TimeLine.this.setCursor(cursor);
            }
        };
        this.addMouseListener(msListener);
        this.addMouseMotionListener(msListener);
        setToolTipText("TimeLine");
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        _displayStart = start;
        _displayEnd = end;
        _model = model;
        _intervalCount = intervals;
        double intervalsInTime = (end.getValue(Time.SECOND) - start.getValue(Time.SECOND)) /
                (double) _intervalCount;
        _intervalInTime = new Time(intervalsInTime, Time.SECOND);


        addMouseListener(_mouseListener);
        addMouseMotionListener(_mouseDragListener);
        addKeyListener(_keyListener);
        _nodes = new TreeSet<>(_comparator);

        _vetoableListeners = Collections.synchronizedList(new ArrayList<>(5));
        setBorder(BorderFactory.createEmptyBorder(20, 0, 70, 0));
    }

    /**
     *
     * add a time line node to the time line
     *
     */
    public void addTimeLineNode(TimeLineNode node) throws IllegalNodePositionException {
        if (!_nodes.contains(node)) {
            TimeLine oldParent = node.getParent();
            try {
                node.setParent(this);
                _nodes.add(node);
                validatePropertyChange(new PropertyChangeEvent(node,
                                                               TimeLine.NODE_ADDED,
                                                               null,
                                                               node));

                node.addVetoableChangeListener(_myChildListener);
                addVetoableChangeListener(node);
                _model.addTimeLineNode(node.getModel());
            } catch (DetailedPropertyVetoException ex) {
                if (oldParent != this) {
                    node.setParent(oldParent);
                    _nodes.remove(node);
                    _model.removeTimeLineNode(node.getModel());
                } else {
                    node.setParent(this);
                    node.addVetoableChangeListener(_myChildListener);
                    addVetoableChangeListener(node);
                }
                throw new IllegalNodePositionException("could not add node: " + node.getTimeLineNodeName());
            }
        }
        repaint();
    }


    /**
     *
     * Add a time line node to the time line and save time by not checking or firing events.
     *
     **/
    public void addTimeLineNodeNoEvents(TimeLineNode node) throws IllegalNodePositionException {
        if (!_nodes.contains(node)) {
            node.setParent(this);
            _nodes.add(node);
            _model.addTimeLineNode(node.getModel());
            repaint();
        }
    }

    public TimeLineModel getModel() {
        return _model;
    }

    /** Set the model (allan: added this method) */
    public void setModel(TimeLineModel model) {
        Time start = model.getStartTime();
        Time end = model.getEndTime();
        int intervals = model.getIntervalCount();

        _displayStart = start;
        _displayEnd = end;
        _model = model;
        _intervalCount = intervals;
        double intervalsInTime = (end.getValue(Time.SECOND) - start.getValue(Time.SECOND)) /
                (double) _intervalCount;
        _intervalInTime = new Time(intervalsInTime, Time.SECOND);
        _nodes = new TreeSet<>(_comparator);

        _vetoableListeners = Collections.synchronizedList(new ArrayList<>(5));
        repaint();
    }

    /**
     *
     * get the unit types to display in the timeline
     *
     **/
    public String getUnitsType() {
        return _unitType;
    }

    /**
     *
     * sets the diplay window of the timeline
     *
     **/
    public synchronized void setDisplayArea(Time start, Time end) {
        if ((_displayStart.getValue() != start.getValue()) || (_displayEnd.getValue() != end.getValue())) {
            Time oldStart = _displayStart;
            Time oldEnd = _displayEnd;
            _displayStart = start;
            _displayEnd = end;
            double intervalsInTime = (_displayEnd.getValue(Time.SECOND) - _displayStart.getValue(Time.SECOND)) /
                    (double) _intervalCount;
            _intervalInTime = new Time(intervalsInTime, Time.SECOND);
            try {
                fireVetoableChange(new PropertyChangeEvent(this, DISPLAY_WINDOW_CHANGE, null, null));
            } catch (DetailedPropertyVetoException ex) {
                setDisplayArea(oldStart, oldEnd);
            }


            repaint();
        }
    }

    /**
     *
     * reset the display window to show all data
     *
     */
    public void resetDisplayArea() {
        setDisplayArea(new Time(getStartTime().getValue()), new Time(getEndTime().getValue()));
    }

    /**
     *
     * move the display window of the timeline by the specified amount
     *
     **/
    public synchronized void moveDisplayAreaBy(Time time) {
        if (time.getValue() != 0.0) {
            Time startTime = new Time(_displayStart.getValue(Time.SECOND) + time.getValue(Time.SECOND),
                                      Time.SECOND);
            Time endTime = new Time(_displayEnd.getValue(Time.SECOND) + time.getValue(Time.SECOND),
                                    Time.SECOND);
            setDisplayArea(startTime, endTime);

        }
        repaint();
    }

    /**
     *
     * remove a time line node from the time line
     *
     **/
    public synchronized void removeTimeLineNode(TimeLineNode node) {
        if (_nodes.contains(node)) {
            try {
                node.removeVetoableChangeListener(_myChildListener);
                node.setParent(null);
                removeVetoableChangeListener(node);
                _nodes.remove(node);
                fireVetoableChange(new PropertyChangeEvent(node, NODE_REMOVED, node, null));
                _model.removeTimeLineNode(node.getModel());
            } catch (DetailedPropertyVetoException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     *
     * get the time value of a single interval in the timeline
     *
     **/
    public Time getIntervalTime() {
        return _intervalInTime;
    }

    /**
     *
     * paint the component
     *
     **/
    protected void paintComponent(Graphics grph) {
        super.paintComponent(grph);

        Graphics2D g2 = (Graphics2D) grph;
        paintCenterLine(g2);
        paintStartLabel(g2);
        paintEndLabel(g2);
        paintNodes(g2);
    }

    /**
     * paint the component
     **/
    protected void paintCenterLine(Graphics2D g2) {
        Dimension dim = getSize();

        // draw the center line
        _centerLine.x1 = _verticalSpacer;
        _centerLine.y1 = dim.height / 2f;
        _centerLine.x2 = dim.width - _verticalSpacer;
        _centerLine.y2 = dim.height / 2f;

        g2.setColor(getBackground());
        g2.fill3DRect((int) _centerLine.x1, (int) _centerLine.y1 - 1, (int) (_centerLine.x2 - _centerLine.x1), 3, false);

        Line2D.Float stopLine = new Line2D.Float();

        // draw left hand stop
        if (_displayStart.getValue(Time.SECOND) <= getStartTime().getValue(Time.SECOND)) {
            stopLine.x1 = _centerLine.x1 - 1;
            stopLine.x2 = _centerLine.x1 - 1;
            stopLine.y1 = (dim.height - _handleHeight) / 2f;
            stopLine.y2 = (dim.height + _handleHeight) / 2f;
            g2.fill3DRect((int) stopLine.x1 - 1, (int) stopLine.y1, 3, (int) (stopLine.y2 - stopLine.y1), false);
        }

        // draw right hand stop
        if (_displayEnd.getValue(Time.SECOND) >= getEndTime().getValue(Time.SECOND)) {
            stopLine.x1 = _centerLine.x2 + 2;
            stopLine.x2 = _centerLine.x2 + 2;
            stopLine.y1 = (dim.height - _handleHeight) / 2f;
            stopLine.y2 = (dim.height + _handleHeight) / 2f;
            g2.fill3DRect((int) stopLine.x1 - 1, (int) stopLine.y1, 3, (int) (stopLine.y2 - stopLine.y1), false);
        }
    }

    /**
     * paint the timeline start label in the current units.
     **/
    protected void paintStartLabel(Graphics2D g2) {
        Dimension dim = getSize();
        String startStr;

        if (DATE_VIEW.equals(_unitType)) {
            DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT);
            startStr = format.format(getDateForTime(_displayStart));
        } else if (TIME_VIEW.equals(_unitType)) {
            HMS hms = new HMS(_displayStart.getValue(Time.HOUR));
            startStr = hms.toString();
        } else {
            DecimalFormat form = new DecimalFormat();
            form.setMaximumFractionDigits(2);
            startStr = form.format(_displayStart.getValue(_unitType));
        }

        Rectangle2D nameBounds = g2.getFontMetrics().getStringBounds(startStr, g2);
        g2.setColor(Color.black);

        float textX = _centerLine.x1 - (float) (1.0 + nameBounds.getWidth() / 2.0);

        float textY;
        if (_labelsAtTop)
            textY = (float) nameBounds.getHeight();
        else {
            int yOff = 32; // additional Y offset for start and end labels (allan)
            textY = (dim.height + _handleHeight) / 2f + (float) (nameBounds.getHeight() + yOff);
        }

        g2.drawString(startStr, textX, textY);
    }

    /**
     * paint the timeline end label in the current units.
     **/
    protected void paintEndLabel(Graphics2D g2) {
        Dimension dim = getSize();
        String endStr;

        if (DATE_VIEW.equals(_unitType)) {
            DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT);
            endStr = format.format(getDateForTime(_displayEnd));
        } else if (TIME_VIEW.equals(_unitType)) {
            HMS hms = new HMS(_displayEnd.getValue(Time.HOUR));
            endStr = hms.toString();
        } else {
            DecimalFormat form = new DecimalFormat();
            form.setMaximumFractionDigits(2);
            endStr = form.format(_displayEnd.getValue(_unitType));
        }

        Rectangle2D nameBounds = g2.getFontMetrics().getStringBounds(endStr, g2);
        g2.setColor(Color.black);

        float textX = _centerLine.x2 + (float) (1.0 - nameBounds.getWidth() / 2.0);
        float textY;
        if (_labelsAtTop)
            textY = (float) nameBounds.getHeight();
        else {
            int yOff = 32; // additional Y offset for start and end labels (allan)
            textY = (dim.height + _handleHeight) / 2f + (float) (nameBounds.getHeight() + yOff);
        }

        g2.drawString(endStr, textX, textY);

        if (!TimeLine.DATE_VIEW.equals(_unitType) && !TimeLine.TIME_VIEW.equals(_unitType)) {
            String unitAbbrev = Time.getUnitsAbbrev(_unitType);
            nameBounds = g2.getFontMetrics().getStringBounds(unitAbbrev, g2);
            textX = _centerLine.x2 + (float) (1.0 - nameBounds.getWidth() / 2.0);
            textY = textY + (float) (nameBounds.getHeight()) - 2f;
            g2.drawString(unitAbbrev, textX, textY);
        }
    }

    /**
     * paint the timeline nodes.
     **/
    protected void paintNodes(Graphics2D g2) {
        for (TimeLineNode node : _nodes) {
            node.paintTimeLineNode(g2);
        }
    }


    /**
     *
     * get the displayed start of the timeline
     *
     **/
    public Time getDisplayStart() {
        return _displayStart;
    }

    /**
     *
     * get the displayed end of the timeline
     *
     **/
    public Time getDisplayEnd() {
        return _displayEnd;
    }

    /**
     *
     * this method handles any key events received by the panel.
     *
     **/
    public void handleKeyEvent(KeyEvent evt) {
        List<TimeLineNode> procList = Collections.synchronizedList(new ArrayList<>(_nodes.size()));
        try {
            for (TimeLineNode node : _nodes) {
                node.handleKeyEvent(evt);
                procList.add(node);
            }
        } catch (DetailedPropertyVetoException ex) {
            procList.forEach(TimeLineNode::revertToPrevious);
        }

    }


    /**
     *
     * add a vetoable property change listener to the time line.
     *
     **/
    public void addVetoableChangeListener(VetoableChangeListener listener) {
        if (!_vetoableListeners.contains(listener)) {
            _vetoableListeners.add(listener);
        }

    }

    /**
     *
     * remove a vetoable property change listener from the time line
     *
     **/
    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        _vetoableListeners.remove(listener);
    }

    /**
     *
     * set the mode of the time line
     *
     **/
    public void setMode(String mode) {
        _mode = mode;
    }


    /**
     *
     * get the time value of an X coordinate in the TimeLine
     *
     **/
    public Time getTimeForPoint(float xValue) {
        double currentWindowWidth = _displayEnd.getValue(Time.SECOND) - _displayStart.getValue(Time.SECOND);

        xValue = xValue - _verticalSpacer;
        double time = (double) xValue * currentWindowWidth /
                ((double) getSize().width - (2.0 * _verticalSpacer));
        time = time + _displayStart.getValue(Time.SECOND);
        return new Time(time, Time.SECOND);
    }

    /**
     *
     * get the x coordinate for specified time
     *
     **/
    public float getPointForTime(Time time) {
        double timeValue = time.getValue(Time.SECOND) - _displayStart.getValue(Time.SECOND);
        double currentWindowWidth = _displayEnd.getValue(Time.SECOND) - _displayStart.getValue(Time.SECOND);
        double xValue = timeValue * ((double) getSize().width - (2.0 * _verticalSpacer)) / currentWindowWidth;
        xValue = xValue + _verticalSpacer;
        return (float) Math.round(xValue);
    }

    /**
     *
     * takes a time and converts it into a date where the new date is
     * equal to the start date + the time
     *
     **/
    public Date getDateForTime(Time time) {
        return _model.getDateForTime(time);
    }


    /**
     *
     * get the starting value in the timeline
     *
     **/
    public Time getStartTime() {
        return _model.getStartTime();
    }

    /**
     *
     * get the ending value of the timeline
     *
     **/
    public Time getEndTime() {
        return _model.getEndTime();
    }


    /**
     *
     * get the list of nodes in the timeline which are currently selected
     *
     **/
    public List<TimeLineNode> getSelectedNodes() {
        return _nodes.stream().filter(TimeLineNode::isSelected).collect(Collectors.toList());
    }

    /**
     *
     * get the time line's mode
     *
     **/
    public String getMode() {
        return _mode;
    }

    /**
     *
     * takes a PropertyChangeEvent and throws a PropertyVetoException if anything in the event
     * would cause it to be rejected
     *
     **/
    protected void validatePropertyChange(PropertyChangeEvent evt) throws DetailedPropertyVetoException {
        TimeLineNode node = (TimeLineNode) evt.getSource();
        if ((node.getStartTime().getValue(Time.SECOND) < getStartTime().getValue(Time.SECOND))) {
            throw new DetailedPropertyVetoException(this, TimeLineNode.HIT_LEFT_EDGE,
                                                    "out of bounds", evt);
        } else if (node.getEndTime().getValue(Time.SECOND) > getEndTime().getValue(Time.SECOND)) {
            throw new DetailedPropertyVetoException(this, TimeLineNode.HIT_RIGHT_EDGE,
                                                    "out of bounds", evt);

        } else {
            if (evt.getPropertyName().equals(TimeLineNode.HIT_LEFT_EDGE)) {
                Time startTime = node.getStartTime();
                Time moveBy = new Time(startTime.getValue(Time.SECOND) - _displayStart.getValue(Time.SECOND),
                                       Time.SECOND);
                moveDisplayAreaBy(moveBy);
            } else if (evt.getPropertyName().equals(TimeLineNode.HIT_RIGHT_EDGE)) {
                Time endTime = node.getEndTime();
                Time moveBy = new Time(endTime.getValue(Time.SECOND) - _displayEnd.getValue(Time.SECOND),
                                       Time.SECOND);
                moveDisplayAreaBy(moveBy);
            } else {

                fireVetoableChange(evt);
            }
        }

    }

    /**
     *
     * fires a vetoable change event to all listeners of the timeline
     *
     **/
    protected void fireVetoableChange(PropertyChangeEvent evt) throws DetailedPropertyVetoException {
        for (Object _vetoableListener : _vetoableListeners) {
            VetoableChangeListener listener = (VetoableChangeListener) _vetoableListener;
            if (listener != evt.getSource()) {
                try {
                    listener.vetoableChange(evt);
                } catch (DetailedPropertyVetoException ex) {
                    throw ex;
                } catch (PropertyVetoException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     *
     * this method handles any mouse clicked events
     *
     **/
    protected void handleMouseClicked(MouseEvent evt) {
        if (_mode.equals(SELECTION_MODE)) {
            handleSelectionEvent(evt);
        } else if (_mode.equals(ZOOM_MODE)) {
            handleZoomEvent(evt);

        }
    }

    /**
     *
     * this method handles any mouse pressed or mouse released events
     *
     **/
    protected void handleMousePressed(MouseEvent evt) {
        if (_mode.equals(SELECTION_MODE)) {
            handleSelectionEvent(evt);
        }
    }

    /**
     *
     * zooms the timeline in or out
     *
     **/
    private void handleZoomEvent(MouseEvent evt) {

        if (SwingUtilities.isRightMouseButton(evt)) {
            zoomOut(evt.getPoint());
        } else {

            zoomIn(evt.getPoint());
        }


    }

    /**
     *
     * zoom the time line in
     *
     */
    public void zoomIn() {
        int x = (int) Math.round((_centerLine.x1 + _centerLine.x2) / 2.0);
        int y = Math.round(_centerLine.y1);
        zoomIn(new Point(x, y));
    }

    /**
     *
     * zoom the time line out
     *
     */
    public void zoomOut() {
        int x = (int) Math.round((_centerLine.x1 + _centerLine.x2) / 2.0);
        int y = Math.round(_centerLine.y1);
        zoomOut(new Point(x, y));
    }

    /**
     *
     * zoom the time line in with center point centerPt being at the center
     *
     * @param centerPt the point to center the zoom around
     *
     */
    public void zoomIn(Point centerPt) {
        Time center = getTimeForPoint(centerPt.x);
        double currentWindowWidth = _displayEnd.getValue(Time.SECOND)
                - _displayStart.getValue(Time.SECOND);

        currentWindowWidth = currentWindowWidth / 2.0;

        if (currentWindowWidth < MIN_DISPLAY_WINDOW) {
            currentWindowWidth = MIN_DISPLAY_WINDOW;
        }


        double startX = Math.floor((center.getValue(Time.SECOND) - currentWindowWidth / 2.0));
        double endX = Math.floor((center.getValue(Time.SECOND) + currentWindowWidth / 2.0));
        if (startX < getStartTime().getValue(Time.SECOND)) {
            double adjust = getStartTime().getValue(Time.SECOND) - startX;
            startX += adjust;
            endX += adjust;
        }
        if (endX > getEndTime().getValue(Time.SECOND)) {
            double adjust = endX - getEndTime().getValue(Time.SECOND);
            startX -= adjust;
            endX -= adjust;
        }
        Time startTime = new Time(startX, Time.SECOND);
        Time endTime = new Time(endX, Time.SECOND);
        setDisplayArea(startTime, endTime);
    }


    /**
     *
     * zoom the time line out with center point centerPt being at the center
     *
     * @param centerPt the point to center the zoom around
     *
     */
    public void zoomOut(Point centerPt) {
        Time center = getTimeForPoint(centerPt.x);
        double currentWindowWidth = _displayEnd.getValue(Time.SECOND)
                - _displayStart.getValue(Time.SECOND);
        currentWindowWidth = currentWindowWidth * 2;
        double maxWidth = getEndTime().getValue(Time.SECOND)
                - getStartTime().getValue(Time.SECOND);
        if (currentWindowWidth > maxWidth) {
            currentWindowWidth = maxWidth;
        }


        double startX = Math.floor((center.getValue(Time.SECOND) - currentWindowWidth / 2.0));
        double endX = Math.floor((center.getValue(Time.SECOND) + currentWindowWidth / 2.0));
        if (startX < getStartTime().getValue(Time.SECOND)) {
            double adjust = getStartTime().getValue(Time.SECOND) - startX;
            startX += adjust;
            endX += adjust;
        }
        if (endX > getEndTime().getValue(Time.SECOND)) {
            double adjust = endX - getEndTime().getValue(Time.SECOND);
            startX -= adjust;
            endX -= adjust;
        }
        Time startTime = new Time(startX, Time.SECOND);
        Time endTime = new Time(endX, Time.SECOND);
        setDisplayArea(startTime, endTime);

    }


    /**
     *
     * propogates a selection event down to all timeline nodes
     *
     **/
    private void handleSelectionEvent(MouseEvent evt) {
        for (TimeLineNode node : _nodes) {
            node.handleMouseEvent(evt);
        }
    }

    /**
     *
     * inner class used for sorting time line nodes
     *
     **/
    private static class TimeLineNodeComparator implements Comparator<TimeLineNode> {

        public int compare(TimeLineNode o1,
                           TimeLineNode o2) {
            double start1 = o1.getStartTime().getValue(Time.SECOND);
            double start2 = o2.getStartTime().getValue(Time.SECOND);
            return (int) Math.round(start1 - start2);

        }
    }

    public String getToolTipText(MouseEvent event) {
        String result = null;
        Point pt = event.getPoint();
        for (TimeLineNode node : _nodes) {
            if (node.containsPoint(pt)) {
                result = node.getDescription(pt);
                break;
            }

        }
        return result;
    }

    /**
     * test main (allan)
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("TimeLine");
        TimeLine timeLine = new TimeLine();

        TimeLineModel model = new DefaultTimeLineModel(0, 40, 10);
        timeLine.setModel(model);

        try {
            timeLine.addTimeLineNode(new BlockTimeLineNode(new Time(0.0 * 60.0), new Time(5.0 * 60.0), "Block 1"));

            String label1 = "Label 1";
            DefaultTimeLineNode node1 = new DefaultTimeLineNode(new Time(8.0 * 60.0), new Time(12.0 * 60.0), label1) {

                public String getDescription(Point pt) {
                    // Note: like tooltip for node, can return plain or HTML text here,
                    // see gov.nasa.gsfc.sea.exposureplanner.gui.ExposureTimeLineNodeModel for example
                    String details = "<html><font size='-2'>";
                    details += "<table width=" + '"' + "100%" + '"' + " border=0 cellpadding=0 cellspacing=0 >";
                    details += "<caption align='TOP'><font siz='-1'><div align=center><b>Node Info</b></div></font></caption>";
                    details += "Test info";
                    details += "</table>";
                    details += "</font></html>";
                    return details;
                }
            };
            timeLine.addTimeLineNode(node1);
            node1.addVetoableChangeListener(evt -> System.out.println("XXX node 1 changed: " + evt));

            String label2 = "Label 2";
            DefaultTimeLineNode node2 = new DefaultTimeLineNode(new Time(24.0 * 60.0), new Time(30.0 * 60.0), label2) {

                public String getDescription(Point pt) {
                    return "Node Description";
                }
            };
            timeLine.addTimeLineNode(node2);
            node2.addVetoableChangeListener(evt -> System.out.println("XXX node 2 changed: " + evt));

            // Note: to reset the timeline to empty, you could do this:
            //TimeLineModel model = new DefaultTimeLineModel(10);
            //timeLine.setModel(model);

        } catch (Exception e) {
            DialogUtil.error(e);
        }

        timeLine.setPreferredSize(new Dimension(400, 100));
        frame.getContentPane().add(new JScrollPane(timeLine), BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
        frame.addWindowListener(new BasicWindowMonitor());
    }
}
