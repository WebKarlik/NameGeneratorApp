import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class NameGeneratorApp {
    private static final String APP_TITLE = "Генератор имен и псевдонимов";
    private static final String APP_DIR_NAME = "NameGeneratorApp";
    private static final String DATA_FILE = "data.json";
    private static final String PHRASES_FILE = "phrases.json";
    private static final String DEFAULT_RESULT_TEXT = "Нажмите кнопку для генерации";
    /** Сколько раз пробуем подобрать фразу, которой ещё не было в истории. */
    private static final int UNIQUE_ATTEMPTS = 50;

    private java.util.List<String> names = new ArrayList<>();
    private java.util.List<String> nicknames = new ArrayList<>();
    private Set<String> generatedPhrases = new LinkedHashSet<>();
    private final Random random = new Random();
    private final Collator collator = Collator.getInstance(Locale.forLanguageTag("ru"));

    private Path dataFile;
    private Path phrasesFile;
    private String currentPhrase;

    private JFrame frame;
    private JLabel resultLabel;
    private JButton copyButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // остаёмся на стандартном оформлении
            }
            try {
                new NameGeneratorApp().initialize();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private void initialize() throws Exception {
        Path dataDir = resolveDataDir();
        dataFile = dataDir.resolve(DATA_FILE);
        phrasesFile = dataDir.resolve(PHRASES_FILE);

        loadData();
        loadPhrases();

        frame = new JFrame(APP_TITLE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(450, 560);
        frame.setMinimumSize(new Dimension(380, 520));
        frame.setLayout(new BorderLayout());
        URL iconUrl = NameGeneratorApp.class.getResource("/icon.png");
        if (iconUrl != null) {
            frame.setIconImage(new ImageIcon(iconUrl).getImage());
        }

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createEmptyBorder(25, 10, 0, 10));
        resultLabel = new JLabel(DEFAULT_RESULT_TEXT, SwingConstants.CENTER);
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 18f));
        resultPanel.add(resultLabel, BorderLayout.CENTER);
        frame.add(resultPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton generateButton = createStyledButton("Сгенерировать фразу");
        generateButton.addActionListener(e -> generatePhrase());

        copyButton = createStyledButton("Скопировать фразу");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyCurrentPhrase());

        JButton addWordsButton = createStyledButton("Добавить новые слова");
        addWordsButton.addActionListener(e -> openAddWordsDialog());

        JButton viewWordsButton = createStyledButton("Просмотреть все слова");
        viewWordsButton.addActionListener(e -> openViewWordsDialog());

        JButton viewPhrasesButton = createStyledButton("Просмотреть все фразы");
        viewPhrasesButton.addActionListener(e -> showAllPhrases());

        JButton clearPhrasesButton = createStyledButton("Очистить список фраз");
        clearPhrasesButton.addActionListener(e -> clearPhrases());

        JButton exitButton = createStyledButton("Выход");
        exitButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(Box.createVerticalStrut(10));
        buttonPanel.add(generateButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(copyButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(addWordsButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(viewWordsButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(viewPhrasesButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(clearPhrasesButton);
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(exitButton);

        frame.add(buttonPanel, BorderLayout.CENTER);

        JLabel madeByLabel = new JLabel("made by webkarlik", SwingConstants.CENTER);
        madeByLabel.setForeground(Color.BLUE.darker());
        madeByLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        madeByLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://github.com/webkarlik"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "Не удалось открыть ссылку",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                madeByLabel.setText("<html><u>made by webkarlik</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                madeByLabel.setText("made by webkarlik");
            }
        });

        JPanel footerPanel = new JPanel();
        footerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        footerPanel.add(madeByLabel);
        frame.add(footerPanel, BorderLayout.SOUTH);

        frame.getRootPane().setDefaultButton(generateButton);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------
    // Хранение данных
    // ------------------------------------------------------------------

    /**
     * Определяет папку с данными. Если рядом с программой (в рабочей папке) уже
     * лежит доступный для записи data.json — работаем с ним (режим разработки
     * и «портативный» режим). Иначе используем папку пользователя:
     * %APPDATA%\NameGeneratorApp в Windows или ~/.namegeneratorapp в других ОС.
     * Это нужно для установленной .exe-версии: в Program Files писать нельзя.
     */
    private static Path resolveDataDir() throws IOException {
        Path workDir = Paths.get("").toAbsolutePath();
        Path local = workDir.resolve(DATA_FILE);
        if (Files.isRegularFile(local) && Files.isWritable(local)) {
            return workDir;
        }

        String appData = System.getenv("APPDATA");
        Path dir = (appData != null && !appData.isEmpty())
                ? Paths.get(appData, APP_DIR_NAME)
                : Paths.get(System.getProperty("user.home"), "." + APP_DIR_NAME.toLowerCase(Locale.ROOT));
        Files.createDirectories(dir);
        return dir;
    }

    private void loadData() throws IOException {
        if (Files.exists(dataFile)) {
            try {
                readWords(readFile(dataFile), this.names, this.nicknames);
                sortWords();
                return;
            } catch (IOException | ParseException | ClassCastException e) {
                System.err.println("Ошибка загрузки файла данных: " + e);
                backupBrokenFile(dataFile);
            }
        }
        initializeDefaultData();
        saveData();
    }

    private void loadPhrases() throws IOException {
        this.generatedPhrases = new LinkedHashSet<>();
        if (Files.exists(phrasesFile)) {
            try {
                JSONObject json = (JSONObject) new JSONParser().parse(readFile(phrasesFile));
                Object array = json.get("generatedPhrases");
                if (array instanceof JSONArray) {
                    for (Object item : (JSONArray) array) {
                        if (item != null) {
                            this.generatedPhrases.add(item.toString());
                        }
                    }
                }
                return;
            } catch (IOException | ParseException | ClassCastException e) {
                System.err.println("Ошибка загрузки файла фраз: " + e);
                backupBrokenFile(phrasesFile);
                this.generatedPhrases = new LinkedHashSet<>();
            }
        }
        savePhrases();
    }

    /** Стандартный набор слов: встроенный в программу data.json либо минимальный список. */
    private void initializeDefaultData() {
        this.names = new ArrayList<>();
        this.nicknames = new ArrayList<>();
        try (InputStream in = NameGeneratorApp.class.getResourceAsStream("/" + DATA_FILE)) {
            if (in != null) {
                readWords(new String(readAll(in), StandardCharsets.UTF_8), this.names, this.nicknames);
            }
        } catch (IOException | ParseException | ClassCastException e) {
            System.err.println("Ошибка чтения встроенных данных: " + e);
        }
        if (this.names.isEmpty()) {
            this.names.addAll(Arrays.asList("Александр", "Мария", "Дмитрий"));
        }
        if (this.nicknames.isEmpty()) {
            this.nicknames.addAll(Arrays.asList("Грозный", "Великий", "Мудрый"));
        }
        sortWords();
    }

    /** Разбирает JSON вида {"names": [...], "nicknames": [...]} и дополняет списки без дубликатов. */
    private int readWords(String content, java.util.List<String> namesTarget,
            java.util.List<String> nicknamesTarget) throws ParseException {
        Object parsed = new JSONParser().parse(content);
        if (!(parsed instanceof JSONObject)) {
            throw new ClassCastException("Ожидался JSON-объект");
        }
        JSONObject json = (JSONObject) parsed;
        return addWords(json.get("names"), namesTarget) + addWords(json.get("nicknames"), nicknamesTarget);
    }

    private int addWords(Object array, java.util.List<String> target) {
        int added = 0;
        if (array instanceof JSONArray) {
            for (Object item : (JSONArray) array) {
                if (item == null) {
                    continue;
                }
                String word = item.toString().trim();
                if (!word.isEmpty() && !containsIgnoreCase(target, word)) {
                    target.add(word);
                    added++;
                }
            }
        }
        return added;
    }

    private void saveData() throws IOException {
        writeWords(dataFile, this.names, this.nicknames);
    }

    private void writeWords(Path file, java.util.List<String> namesList,
            java.util.List<String> nicknamesList) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendArray(sb, "names", namesList);
        sb.append(",\n");
        appendArray(sb, "nicknames", nicknamesList);
        sb.append("\n}\n");
        writeFile(file, sb.toString());
    }

    private void savePhrases() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendArray(sb, "generatedPhrases", this.generatedPhrases);
        sb.append("\n}\n");
        writeFile(phrasesFile, sb.toString());
    }

    private static void appendArray(StringBuilder sb, String key, java.util.Collection<String> values) {
        sb.append("  \"").append(key).append("\": [\n");
        int index = 0;
        for (String value : values) {
            sb.append("    \"").append(JSONValue.escape(value)).append("\"");
            if (++index < values.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]");
    }

    private static String readFile(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        // BOM добавляют некоторые редакторы Windows (например, Блокнот)
        return content.startsWith("﻿") ? content.substring(1) : content;
    }

    /** Пишет файл в UTF-8 через временный файл, чтобы не испортить данные при сбое. */
    private static void writeFile(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Сохраняет повреждённый файл рядом как *.bak, чтобы данные пользователя не пропали. */
    private static void backupBrokenFile(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Не удалось создать резервную копию: " + e);
        }
    }

    private void sortWords() {
        this.names.sort(collator);
        this.nicknames.sort(collator);
    }

    private static boolean containsIgnoreCase(java.util.Collection<String> words, String word) {
        for (String existing : words) {
            if (existing.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Генерация фраз
    // ------------------------------------------------------------------

    private void generatePhrase() {
        if (this.names.isEmpty() || this.nicknames.isEmpty()) {
            currentPhrase = null;
            copyButton.setEnabled(false);
            resultLabel.setText("Недостаточно слов для генерации");
            return;
        }

        // Стараемся выдать фразу, которой ещё не было в истории
        String phrase = randomPhrase();
        for (int i = 0; i < UNIQUE_ATTEMPTS && this.generatedPhrases.contains(phrase); i++) {
            phrase = randomPhrase();
        }

        currentPhrase = phrase;
        resultLabel.setText(phrase);
        resultLabel.setToolTipText(phrase);
        copyButton.setEnabled(true);

        if (this.generatedPhrases.add(phrase)) {
            try {
                savePhrases();
            } catch (IOException e) {
                e.printStackTrace();
                showError(frame, "Ошибка сохранения фраз: " + e.getMessage());
            }
        }
    }

    private String randomPhrase() {
        return this.names.get(random.nextInt(this.names.size())) + " "
                + this.nicknames.get(random.nextInt(this.nicknames.size()));
    }

    private void copyCurrentPhrase() {
        if (currentPhrase != null) {
            copyToClipboard(currentPhrase);
        }
    }

    private void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception e) {
            showError(frame, "Не удалось скопировать в буфер обмена");
        }
    }

    private void clearPhrases() {
        if (this.generatedPhrases.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Список фраз уже пуст",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                "Вы уверены, что хотите очистить список всех фраз?",
                "Подтверждение очистки", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            this.generatedPhrases.clear();
            try {
                savePhrases();
                JOptionPane.showMessageDialog(frame, "Список всех фраз очищен",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                showError(frame, "Ошибка сохранения: " + e.getMessage());
            }
        }
    }

    private void showAllPhrases() {
        if (this.generatedPhrases.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Нет сохраненных фраз",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(frame, "Все сгенерированные фразы (" + this.generatedPhrases.size() + ")", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String phrase : this.generatedPhrases) {
            listModel.addElement(phrase);
        }

        JList<String> phrasesList = new JList<>(listModel);
        phrasesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(phrasesList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dialog.add(scrollPane, BorderLayout.CENTER);

        JButton copySelectedButton = new JButton("Копировать выбранную");
        copySelectedButton.addActionListener(e -> {
            String selected = phrasesList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "Выберите фразу",
                        "Ошибка", JOptionPane.WARNING_MESSAGE);
            } else {
                copyToClipboard(selected);
            }
        });

        JButton saveButton = new JButton("Сохранить в файл...");
        saveButton.addActionListener(e -> exportPhrases(dialog));

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        buttonPanel.add(copySelectedButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(closeButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        closeOnEscape(dialog);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void exportPhrases(Component parent) {
        Path target = chooseFile(parent, true, "Текстовые файлы (*.txt)", "txt", "phrases.txt");
        if (target == null) {
            return;
        }
        try {
            String text = String.join(System.lineSeparator(), this.generatedPhrases) + System.lineSeparator();
            Files.write(target, text.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(parent, "Фразы сохранены в файл:\n" + target,
                    "Успех", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            showError(parent, "Ошибка сохранения: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Управление словами
    // ------------------------------------------------------------------

    private void openAddWordsDialog() {
        JDialog dialog = new JDialog(frame, "Добавить слова", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(350, 180);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 10));

        JLabel categoryLabel = new JLabel("Категория:");
        JComboBox<String> categoryCombo = new JComboBox<>(new String[] { "Имя", "Псевдоним" });

        JLabel wordLabel = new JLabel("Слово:");
        JTextField wordField = new JTextField();

        inputPanel.add(categoryLabel);
        inputPanel.add(categoryCombo);
        inputPanel.add(wordLabel);
        inputPanel.add(wordField);

        JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

        JButton addButton = new JButton("Добавить");
        addButton.addActionListener(e -> {
            String word = wordField.getText().trim();
            if (word.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Введите слово!",
                        "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }

            java.util.List<String> targetList = (categoryCombo.getSelectedIndex() == 0)
                    ? this.names
                    : this.nicknames;

            if (containsIgnoreCase(targetList, word)) {
                JOptionPane.showMessageDialog(dialog, "Такое слово уже существует!",
                        "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }

            targetList.add(word);
            targetList.sort(collator);
            try {
                saveData();
                wordField.setText("");
                statusLabel.setText("Добавлено: " + word);
            } catch (IOException ex) {
                ex.printStackTrace();
                targetList.remove(word);
                showError(dialog, "Ошибка сохранения: " + ex.getMessage());
            }
            wordField.requestFocusInWindow();
        });

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(closeButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        southPanel.add(statusLabel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.getRootPane().setDefaultButton(addButton);
        closeOnEscape(dialog);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 350), dialog.getHeight());
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void openViewWordsDialog() {
        JDialog dialog = new JDialog(frame, "Просмотр слов", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(520, 420);

        JTabbedPane tabbedPane = new JTabbedPane();
        DefaultListModel<String> namesModel = new DefaultListModel<>();
        DefaultListModel<String> nicknamesModel = new DefaultListModel<>();

        Runnable refresh = () -> {
            fillModel(namesModel, this.names);
            fillModel(nicknamesModel, this.nicknames);
            tabbedPane.setTitleAt(0, "Имена (" + this.names.size() + ")");
            tabbedPane.setTitleAt(1, "Псевдонимы (" + this.nicknames.size() + ")");
        };

        tabbedPane.addTab("Имена", createWordsPanel(this.names, namesModel, refresh));
        tabbedPane.addTab("Псевдонимы", createWordsPanel(this.nicknames, nicknamesModel, refresh));
        refresh.run();

        dialog.add(tabbedPane, BorderLayout.CENTER);

        JButton importButton = new JButton("Импорт...");
        importButton.addActionListener(e -> {
            importWords(dialog);
            refresh.run();
        });

        JButton exportButton = new JButton("Экспорт...");
        exportButton.addActionListener(e -> exportWords(dialog));

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel closePanel = new JPanel();
        closePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        closePanel.add(importButton);
        closePanel.add(exportButton);
        closePanel.add(closeButton);
        dialog.add(closePanel, BorderLayout.SOUTH);

        closeOnEscape(dialog);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private static void fillModel(DefaultListModel<String> model, java.util.List<String> words) {
        model.clear();
        for (String word : words) {
            model.addElement(word);
        }
    }

    private JPanel createWordsPanel(java.util.List<String> words, DefaultListModel<String> listModel,
            Runnable refresh) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JList<String> wordsList = new JList<>(listModel);
        wordsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(wordsList);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        JButton deleteButton = new JButton("Удалить выбранное");
        deleteButton.addActionListener(e -> {
            int selectedIndex = wordsList.getSelectedIndex();
            if (selectedIndex == -1) {
                JOptionPane.showMessageDialog(panel, "Выберите слово для удаления",
                        "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String word = words.get(selectedIndex);
            int confirm = JOptionPane.showConfirmDialog(panel,
                    "Вы уверены, что хотите удалить слово «" + word + "»?",
                    "Подтверждение удаления", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                words.remove(selectedIndex);
                try {
                    saveData();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    words.add(selectedIndex, word);
                    showError(panel, "Ошибка сохранения: " + ex.getMessage());
                }
                refresh.run();
                if (!listModel.isEmpty()) {
                    wordsList.setSelectedIndex(Math.min(selectedIndex, listModel.size() - 1));
                }
            }
        });

        buttonPanel.add(deleteButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /** Экспорт списков слов в JSON-файл того же формата, что и data.json. */
    private void exportWords(Component parent) {
        Path target = chooseFile(parent, true, "Файлы JSON (*.json)", "json", "words.json");
        if (target == null) {
            return;
        }
        try {
            writeWords(target, this.names, this.nicknames);
            JOptionPane.showMessageDialog(parent, "Слова сохранены в файл:\n" + target,
                    "Успех", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            showError(parent, "Ошибка экспорта: " + e.getMessage());
        }
    }

    /** Импорт слов из JSON-файла: новые слова добавляются к существующим, дубликаты пропускаются. */
    private void importWords(Component parent) {
        Path source = chooseFile(parent, false, "Файлы JSON (*.json)", "json", null);
        if (source == null) {
            return;
        }

        java.util.List<String> newNames = new ArrayList<>(this.names);
        java.util.List<String> newNicknames = new ArrayList<>(this.nicknames);
        int added;
        try {
            added = readWords(readFile(source), newNames, newNicknames);
        } catch (IOException e) {
            showError(parent, "Не удалось прочитать файл: " + e.getMessage());
            return;
        } catch (ParseException | ClassCastException e) {
            showError(parent, "Файл имеет неверный формат.\n"
                    + "Ожидается JSON с массивами \"names\" и \"nicknames\".");
            return;
        }

        if (added == 0) {
            JOptionPane.showMessageDialog(parent, "Новых слов в файле не найдено",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        java.util.List<String> oldNames = this.names;
        java.util.List<String> oldNicknames = this.nicknames;
        // списки используются панелями диалога, поэтому меняем их содержимое, а не ссылки
        int namesAdded = newNames.size() - oldNames.size();
        int nicknamesAdded = newNicknames.size() - oldNicknames.size();
        java.util.List<String> namesBackup = new ArrayList<>(oldNames);
        java.util.List<String> nicknamesBackup = new ArrayList<>(oldNicknames);
        replaceContent(oldNames, newNames);
        replaceContent(oldNicknames, newNicknames);
        sortWords();
        try {
            saveData();
            JOptionPane.showMessageDialog(parent,
                    "Добавлено имен: " + namesAdded + "\nДобавлено псевдонимов: " + nicknamesAdded,
                    "Успех", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            replaceContent(oldNames, namesBackup);
            replaceContent(oldNicknames, nicknamesBackup);
            showError(parent, "Ошибка сохранения: " + e.getMessage());
        }
    }

    private static void replaceContent(java.util.List<String> target, java.util.List<String> source) {
        target.clear();
        target.addAll(source);
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы интерфейса
    // ------------------------------------------------------------------

    private Path chooseFile(Component parent, boolean save, String description, String extension,
            String defaultName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (defaultName != null) {
            chooser.setSelectedFile(new File(defaultName));
        }

        int result = save ? chooser.showSaveDialog(parent) : chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path path = chooser.getSelectedFile().toPath();
        if (save) {
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                path = path.resolveSibling(path.getFileName() + "." + extension);
            }
            if (Files.exists(path)) {
                int confirm = JOptionPane.showConfirmDialog(parent,
                        "Файл уже существует. Заменить его?",
                        "Подтверждение", JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) {
                    return null;
                }
            }
        }
        return path;
    }

    private static void closeOnEscape(JDialog dialog) {
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(250, 40));
        button.setPreferredSize(new Dimension(250, 40));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setFocusPainted(false);
        return button;
    }
}
