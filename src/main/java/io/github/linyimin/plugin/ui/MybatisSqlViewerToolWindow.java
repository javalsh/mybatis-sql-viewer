package io.github.linyimin.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.ui.JBUI;
import io.github.linyimin.plugin.configuration.MybatisDatasourceStateComponent;
import io.github.linyimin.plugin.configuration.model.DatasourceConfiguration;
import io.github.linyimin.plugin.constant.Constant;
import io.github.linyimin.plugin.configuration.MybatisSqlStateComponent;
import io.github.linyimin.plugin.component.SqlParamGenerateComponent;
import io.github.linyimin.plugin.configuration.model.MybatisSqlConfiguration;
import io.github.linyimin.plugin.utils.MybatisSqlUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;


/**
 * @author yiminlin
 * @date 2022/02/01 12:31 下午
 **/
public class MybatisSqlViewerToolWindow extends SimpleToolWindowPanel {

    private JTextField methodName;
    private JTabbedPane tabbedPane;
    private JTextField host;
    private JTextField port;
    private JTextField user;
    private JTextField database;
    private JPasswordField password;
    private JTextField url;
    private JButton connectionTestButton;
    private JPanel root;
    private JTextArea connectionInfoTextArea;

    private JTextArea result;

    private final RSyntaxTextArea sqlText;
    private JPanel sqlPanel;
    private final RTextScrollPane sqlScroll;

    private JPanel paramsPanel;
    private final RTextScrollPane paramsScroll;
    private final RSyntaxTextArea paramsText;

    private JScrollPane resultScroll;
    private JTable tableSchema;
    private JScrollPane tableSchemaScroll;

    private final Project myProject;


    public JPanel getRoot() {
        return root;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    @Override
    public JPanel getContent() {
        return getRoot();
    }

    public MybatisSqlViewerToolWindow(ToolWindow toolWindow, Project project) {

        super(true, false);
        this.myProject = project;

        paramsText = CustomTextField.createArea("json");
        sqlText = CustomTextField.createArea("sql");

        sqlPanel.setLayout(new BorderLayout());

        sqlScroll = new RTextScrollPane(sqlText);
        sqlScroll.setBorder(new EmptyBorder(JBUI.emptyInsets()));
        sqlPanel.add(sqlScroll);

        paramsPanel.setLayout(new BorderLayout());

        paramsScroll = new RTextScrollPane(paramsText);
        paramsScroll.setBorder(new EmptyBorder(JBUI.emptyInsets()));
        paramsPanel.add(paramsScroll);

        setScrollUnitIncrement();

        addComponentListener();

        MybatisDatasourceStateComponent component = myProject.getComponent(MybatisDatasourceStateComponent.class);
        host.setText(component.getHost());
        port.setText(component.getPort());
        user.setText(component.getUser());
        password.setText(component.getPassword());
        database.setText(component.getDatabase());

        String urlText = String.format(Constant.DATABASE_URL_TEMPLATE, component.getHost(), component.getPort(), component.getDatabase());
        url.setText(urlText);

    }


    /**
     * 刷新tool window配置内容
     */
    public void refresh(Project project) {
        MybatisSqlConfiguration config = project.getService(MybatisSqlStateComponent.class).getConfiguration();
        assert config != null;

        methodName.setText(config.getMethod());

        paramsText.setText(config.getParams());

        sqlText.setText(config.getSql());

        result.setText(config.getResult());

        // 默认每次打开，都展示第一个tab
        tabbedPane.setSelectedIndex(0);

    }

    private void addComponentListener() {
        host.getDocument().addDocumentListener(new DatasourceChangeListener());
        port.getDocument().addDocumentListener(new DatasourceChangeListener());
        database.getDocument().addDocumentListener(new DatasourceChangeListener());

        paramsText.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateParams();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateParams();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateParams();
            }

            private void updateParams() {
                MybatisSqlConfiguration config = myProject.getService(MybatisSqlStateComponent.class).getConfiguration();
                assert config != null;
                config.setParams(paramsText.getText());
            }
        });

        // 监听button点击事件
        connectionTestButton.addActionListener((e) -> {

            String hostText = host.getText();
            String portText = port.getText();
            String userText = user.getText();
            String passwordText = String.valueOf(password.getPassword());
            String databaseText = database.getText();

            String urlText = String.format(Constant.DATABASE_URL_TEMPLATE, hostText, portText, databaseText);

            String connectionInfo = MybatisSqlUtils.mysqlConnectTest(urlText, userText, passwordText);
            connectionInfoTextArea.setText(connectionInfo);

            updateDatasourceForPersistent();
        });

        // 监听tabbedpane点击事件
        tabbedPane.addChangeListener(e -> {

            int selectedIndex = tabbedPane.getSelectedIndex();

            // 点击sql tab时生成sql
            if (selectedIndex == TabbedComponentType.sql.index) {
                sqlText.setText("Loading...");
                generateSql();
            }

            // 点击result tab时执行sql语句并展示结果
            if (selectedIndex == TabbedComponentType.result.index) {
                result.setText("Loading...");
                generateSql();
                executeSql();
            }

            // 点击table tab时获取table的schema信息
            if (selectedIndex == TabbedComponentType.table.index) {
                acquireTableSchema();
            }
        });
    }

    private void updateDatasourceForPersistent() {
        MybatisDatasourceStateComponent component = myProject.getComponent(MybatisDatasourceStateComponent.class);

        component.getState()
                .host(host.getText())
                .port(port.getText())
                .user(user.getText())
                .password(String.valueOf(password.getPassword()))
                .database(database.getText());

    }

    private void acquireTableSchema() {

        // 获取表列信息：DESC mybatis.CITY;
        // 获取表信息(编码)：show table status from `global_ug_usm_ae` like  'houyi_clc_plan';

        String sql = "show table status from `global_ug_usm_ae` like  'houyi_clc_plan';";
        String urlText = String.format(Constant.DATABASE_URL_TEMPLATE, host.getText(), port.getText(), database.getText());
        String passwordText = String.valueOf(password.getPassword());

        try {
            DefaultTableModel model = MybatisSqlUtils.acquireTableSchema(urlText, user.getText(), passwordText, sql);
            if (model == null) {
                Messages.showInfoMessage("acquire table schema fail", "Table Schema");
            } else {
                tableSchema.setModel(model);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateSql() {
        try {
            SqlParamGenerateComponent generateService = myProject.getService(SqlParamGenerateComponent.class);

            MybatisSqlConfiguration sqlConfig = myProject.getService(MybatisSqlStateComponent.class).getConfiguration();
            assert sqlConfig != null;

            String sqlStr = generateService.generateSql(myProject, sqlConfig.getMethod(), sqlConfig.getParams());
            sqlConfig.setSql(sqlStr);

            sqlText.setText(sqlStr);
        } catch (Throwable e) {
            Messages.showInfoMessage("generate sql error. err: " + e.getMessage(), Constant.APPLICATION_NAME);
        }
    }

    private void executeSql() {
        String urlText = String.format(Constant.DATABASE_URL_TEMPLATE, host.getText(), port.getText(), database.getText());

        String passwordText = String.valueOf(password.getPassword());
        String resultText;
        try {
            resultText = MybatisSqlUtils.executeSql(urlText, user.getText(), passwordText, sqlText.getText());
        } catch (SQLException e) {
            resultText = "Execute Sql Failed. err: " + e.getMessage();
        }

        MybatisSqlConfiguration sqlConfig = myProject.getService(MybatisSqlStateComponent.class).getConfiguration();
        assert sqlConfig != null;
        sqlConfig.setResult(resultText);

        result.setText(resultText);

    }

    private class DatasourceChangeListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            updateUrlTextField();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateUrlTextField();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateUrlTextField();
        }

        private void updateUrlTextField() {
            String hostText = host.getText();
            String portText = port.getText();
            String databaseText = database.getText();
            String urlText = String.format(Constant.DATABASE_URL_TEMPLATE, hostText, portText, databaseText);
            url.setText(urlText);
        }
    }

    private void scrollPanelConfig() {

    }

    private void setScrollUnitIncrement() {
        int unit = 16;
        this.sqlScroll.getVerticalScrollBar().setUnitIncrement(unit);
        this.sqlScroll.getHorizontalScrollBar().setUnitIncrement(unit);

        this.resultScroll.getVerticalScrollBar().setUnitIncrement(unit);
        this.resultScroll.getHorizontalScrollBar().setUnitIncrement(unit);

        this.paramsScroll.getVerticalScrollBar().setUnitIncrement(unit);
        this.paramsScroll.getHorizontalScrollBar().setUnitIncrement(unit);
    }

    private enum TabbedComponentType {
        /**
         * Tanned类型对应的index
         */
        params(0),
        sql(1),
        result(2),
        datasource(3),

        table(4);

        private final int index;

        TabbedComponentType(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}
