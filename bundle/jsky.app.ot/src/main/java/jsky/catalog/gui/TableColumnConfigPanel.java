// Copyright 2001 Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
//
// $Id: TableColumnConfigPanel.java 4414 2004-02-03 16:21:36Z brighton $
//

package jsky.catalog.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import jsky.util.I18N;


/**
 * A panel for choosing which table columns to display.
 */
public class TableColumnConfigPanel extends JPanel {

    // Used to access internationalized strings (see i18n/gui*.proprties)
    private static final I18N _I18N = I18N.getInstance(TableColumnConfigPanel.class);

    // The widget displaying the table
    private TableDisplay _tableDisplay;

    // The table displaying the available table columns
    private JTable _table;


    /**
     * Constructor.
     *
     * @param tableDisplay the widget displaying the table
     */
    public TableColumnConfigPanel(TableDisplay tableDisplay) {
        _tableDisplay = tableDisplay;

        _table = new JTable();
        _table.setBackground(getBackground());

        JTableHeader header = _table.getTableHeader();
        header.setUpdateTableInRealTime(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));


        setModel((DefaultTableModel) _tableDisplay.getTableQueryResult());

        setLayout(new BorderLayout());
        add(new JScrollPane(_table), BorderLayout.CENTER);
    }


    /** Set the table model to use for the table displaying the column names and checkboxes. */
    public void setModel(DefaultTableModel model) {
        final Vector<String> _columnNames = new Vector<>(2);
        _columnNames.add(_I18N.getString("colName"));
        _columnNames.add(_I18N.getString("showQM"));

        final String[] _columnIdentifiers = _getColumnIdentifiers(model);
        boolean[] show = _tableDisplay.getShow();
        final Vector<Object> data = new Vector<>(_columnIdentifiers.length);

        if (show != null && show.length != _columnIdentifiers.length)
            show = null; // table columns might have changed since last session

        for (int i = 0; i < _columnIdentifiers.length; i++) {
            Vector<Object> row = new Vector<>(2);
            row.add(_columnIdentifiers[i]);
            row.add(show != null ? Boolean.valueOf(show[i]) : Boolean.TRUE);
            data.add(row);
        }

        _table.setModel(new DefaultTableModel(data, _columnNames) {

            public Class getColumnClass(int columnIndex) {
                if (columnIndex == 0)
                    return String.class;
                return Boolean.class;
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return (columnIndex == 1);
            }
        });
    }


    /** Return an array of column names */
    private String[] _getColumnIdentifiers(DefaultTableModel model) {
        String[] ar = new String[model.getColumnCount()];
        for (int i = 0; i < ar.length; i++)
            ar[i] = model.getColumnName(i);
        return ar;
    }

    /** Apply any changes */
    @SuppressWarnings("unchecked")
    public void apply() {
        DefaultTableModel model = (DefaultTableModel) _table.getModel();
        Vector<Vector<Object>> data = model.getDataVector();
        int numCols = data.size();
        boolean[] show = new boolean[numCols];
        for (int i = 0; i < numCols; i++) {
            Vector<Object> row = data.get(i);
            show[i] = (Boolean)row.get(1);
        }
        _tableDisplay.setShow(show);
    }


    /** Cancel any changes */
    public void cancel() {
        setModel((DefaultTableModel) _tableDisplay.getTableQueryResult());
    }
}

