//(c) ISCTE-IUL, Pedro Ramos, 2022

//import org.bson.Document;
//import org.bson.*;
//import org.bson.conversions.*;

//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.json.JSONException;

import java.io.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.sql.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import javax.swing.text.BadLocationException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

///////////////////////////////////////////////////////////////////IMPORTES PARA TESTE////////////////////////////////////////////////////////////////
import java.util.regex.Pattern;
import com.mongodb.*;
import com.mongodb.util.JSON;

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
public class WriteMysql {

	// Objetos Mongo
	static MongoClient mongoClient;
	static DB db;
	static DBCollection colDoors;
	static DBCollection colTemp1;
	static DBCollection colTemp2;

	// Objeto SQL
	static Connection connTo;

	static JTextArea documentLabel = new JTextArea("\n");

	// Dados do SQL do ficheiro ini
	static String sql_database_connection_to = new String();
	static String sql_database_password_to = new String();
	static String sql_database_user_to = new String();
	static String sql_table_to = new String();

	// Dados mongo do ficheiro ini
	static String mongo_user = new String();
	static String mongo_password = new String();
	static String mongo_address = new String();
	static String mongo_replica = new String();
	static String mongo_database = new String();
	static String mongo_doors = new String();
	static String mongo_temp1 = new String();
	static String mongo_temp2 = new String();
	static String mongo_authentication = new String();

	LinkedHashSet<String> dadoSet = new LinkedHashSet<>();
	private final static int OUTLIERS = 16;
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	HashMap<Integer, Integer> salasMap = new HashMap<>();
	private final static int MAXRATOS = 10;

	private static void createWindow() {
		JFrame frame = new JFrame("Data Bridge");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JLabel textLabel = new JLabel("Data : ", SwingConstants.CENTER);
		textLabel.setPreferredSize(new Dimension(600, 30));
		JScrollPane scroll = new JScrollPane(documentLabel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.setPreferredSize(new Dimension(600, 200));
		JButton b1 = new JButton("Stop the program");
		frame.getContentPane().add(textLabel, BorderLayout.PAGE_START);
		frame.getContentPane().add(scroll, BorderLayout.CENTER);
		frame.getContentPane().add(b1, BorderLayout.PAGE_END);
		frame.setLocationRelativeTo(null);
		frame.pack();
		frame.setVisible(true);
		b1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				System.exit(0);
			}
		});
	}

	public void connectDatabase_to() {
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			connTo = DriverManager.getConnection(sql_database_connection_to, sql_database_user_to,
					sql_database_password_to);
			documentLabel.append("SQl Connection:" + sql_database_connection_to + "\n");
			documentLabel
					.append("Connection To MariaDB Destination " + sql_database_connection_to + " Suceeded" + "\n");
		} catch (Exception e) {
			System.out.println("Mysql Server Destination down, unable to make the connection. " + e);
		}
	}

	// Ligação ao MONGODB
	public void connectToMongo() {
		String mongoURI = new String();
		mongoURI = "mongodb://";
		if (mongo_authentication.equals("true"))
			mongoURI = mongoURI + mongo_user + ":" + mongo_password + "@";
		mongoURI = mongoURI + mongo_address;
		if (!mongo_replica.equals("false"))
			if (mongo_authentication.equals("true"))
				mongoURI = mongoURI + "/?replicaSet=" + mongo_replica + "&authSource=admin";
			else
				mongoURI = mongoURI + "/?replicaSet=" + mongo_replica;
		else if (mongo_authentication.equals("true"))
			mongoURI = mongoURI + "/?authSource=admin";
		MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoURI));
		db = mongoClient.getDB(mongo_database);
		// 3 coleções que precisas
		System.out.println(mongo_doors);
		System.out.println(mongo_temp1);
		System.out.println(mongo_temp2);
		System.out.println(db);
		colDoors = db.getCollection(mongo_doors);
		colTemp1 = db.getCollection(mongo_temp1);
		colTemp2 = db.getCollection(mongo_temp2);
	}

	// Lê a coleção dada como parâmetro e retorna uma lista de DBObjects
	// Para leres estes objetos podes usar o .get(key) que ele retorna o value
	public List<DBObject> readFromMongo(DBCollection col) {
		List<DBObject> results = null;

		// query = new BasicDBObject("i", new BasicDBObject("$gt", 52));

		try (DBCursor resultado = col.find()) {
			results = resultado.toArray();
		}
		return results;
	}

	public void ReadData() {
		// Começa e acaba quando:
		// Hora ser 2000-01-01 00:00:00.000
		// e se sala origem e destino for 0

		int i = 0;
		while (i < 10000) {
			float now = System.nanoTime();

			ArrayList<String> dateListTemperatura = new ArrayList<String>();
			ArrayList<String> dateListRatos = new ArrayList<>();
			List<DBObject> sensors = readFromMongo(colDoors);
			List<DBObject> temp1 = readFromMongo(colTemp1);
			List<DBObject> temp2 = readFromMongo(colTemp2);

			for (DBObject sensor : sensors) {
				String data = sensor.toString();
				dateListRatos.add(data);
				int salaOrigem = (int) sensor.get("SalaOrigem");
				int salaDestino = (int) sensor.get("SalaDestino");
				if (!salasMap.containsKey(salaOrigem))
					salasMap.put(salaOrigem, 0);
				if (!salasMap.containsKey(salaDestino))
					salasMap.put(salaDestino, 0);
			}

			boolean addToTemp1 = true; // Flag para alternar entre temp1 e temp2

			// Iterar enquanto houverem elementos em temp1 ou temp2
			while (!temp1.isEmpty() || !temp2.isEmpty()) {
				if (addToTemp1 && !temp1.isEmpty()) {
					String data = temp1.remove(0).toString();
					dateListTemperatura.add(data);
				} else if (!temp2.isEmpty()) {
					String data = temp2.remove(0).toString();
					dateListTemperatura.add(data);
				}
				addToTemp1 = !addToTemp1; // Alternar a flag
			}

			validarFormatosTemperatura(dateListTemperatura);
			validarFormatosSalas(dateListRatos);

			writeArrayListToFile(dateListTemperatura, "DadosMongoTemperatura.txt");
			writeArrayListToFile(dateListRatos, "DadosMongoSalas.txt");

			float end = System.nanoTime();
			float time = (end - now) / 1000000000;

			if (time >= 0 && time <= 3000) {
				try {
					Thread.sleep(3000 - (long) time);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			i++;
		}

	}

	private void validarFormatosSalas(ArrayList<String> dateListRatos) {
		ArrayList<String> dadosAnomalos = new ArrayList<String>();
		ArrayList<String> dadosCorretos = new ArrayList<String>();
		for (String data : dateListRatos) {
			boolean anomalia = false; // Variável para verificar se é uma anomalia
			if (!data.contains("Hora") || !data.contains("SalaDestino") || !data.contains("SalaOrigem")) {
				dadosAnomalos.add(data);
				anomalia = true;
			}
			if (!anomalia) { // Se não for uma anomalia
				String[] campos = data.split(", ");
				for (String campo : campos) {
					String[] partes = campo.split(": ");
					if (partes.length == 2) {
						String chave = partes[0].trim();
						String valor = partes[1].trim();
						if (chave.equals("Hora"))
							if (!Pattern.matches("'\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3,}'", valor)) {
								// System.err.println("VALOR "+valor);
								dadosAnomalos.add(data);
								// System.err.println("HORA MAL FORMATADA");
								anomalia = true; // Define como anomalia se Hora estiver mal formatada
							} else if (chave.equals("SalaDestino")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {
									dadosAnomalos.add(data);
									// System.err.println("LEITURA MAL FORMATADA");
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							} else if (chave.equals("SalaOrigem")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {
									dadosAnomalos.add(data);
									// System.err.println("LEITURA MAL FORMATADA");
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							}
					}
				}
				if (!anomalia) // Se não for uma anomalia, adicione aos dados corretos
					dadosCorretos.add(data);
			}
		}

		for (String data : dadosCorretos) {
			WriteToMySQL(com.mongodb.util.JSON.serialize(data));
			// TODO: Salvar data no ficheiro txt para backup
		}

		writeArrayListToFile(dadosCorretos, "DadosCorretosSalas.txt");
		writeArrayListToFile(dadosAnomalos, "DadosAnomalosSalas.txt");

		// System.out.println("\n\n\n\n A DETETAR OUTLIERS:\n\n");
		moverRatos(dadosCorretos);
	}

	private void moverRatos(ArrayList<String> dadosCorretos) {
		for (String data : dadosCorretos) {
			int salaOrigem = extractRoom(data, "SalaOrigem");
			int salaDestino = extractRoom(data, "SalaDestino");

			// Atualiza as contagens das salas
			salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
			salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
			if (salasMap.get(salaDestino) >= MAXRATOS) {
				System.out.println("A sala " + salaDestino + " já está lotada. Não é possível adicionar mais ratos.");
				break;
			}
			if (salasMap.get(salaOrigem) < 0) {
				// TODO
				System.out.println("A sala " + salaOrigem + " tem valores negativos. Algo de errado ocorreu.");
			}
		}
		writeMapToFile(salasMap, "DadosMapaSalas.txt");
	}

	public int extractRoom(String data, String key) {
		// Remove os espaços em branco e as vírgulas extras e, em seguida, divide a
		// string pelos espaços restantes
		String[] partes = data.replaceAll("[{}]", "").split("\\s*,\\s*");
		for (String parte : partes) {
			// Divide a parte atual pelos dois pontos
			String[] chaveValor = parte.split("\\s*:\\s*");
			// Verifica se a chave atual corresponde à chave desejada
			if (chaveValor[0].equals("\"" + key + "\"")) {
				// Obtém o valor da chave e remove as aspas e espaços em branco
				String valorSala = chaveValor[1].replaceAll("\"", "").trim();
				// Retorna o valor da sala como um inteiro
				return Integer.parseInt(valorSala);
			}
		}
		// Retorna -1 se a chave não for encontrada
		return -1;
	}

	public void validarFormatosTemperatura(ArrayList<String> dateListTemperatura) {
		ArrayList<String> dadosAnomalos = new ArrayList<String>();
		ArrayList<String> dadosCorretos = new ArrayList<String>();
		for (String data : dateListTemperatura) {
			// System.out.println(data);
			boolean anomalia = false; // Variável para verificar se é uma anomalia
			if (!data.contains("Hora") || !data.contains("Leitura")) {
				dadosAnomalos.add(data);
				System.err.println("HORA OU LEITURA NÃO ENCONTRADOS");
				anomalia = true; // Define como anomalia se Hora ou Leitura não forem encontrados
			}
			if (!anomalia) { // Se não for uma anomalia
				String[] campos = data.split(", ");
				for (String campo : campos) {
					String[] partes = campo.split(": ");
					if (partes.length == 2) {
						String chave = partes[0].trim();
						String valor = partes[1].trim();
						if (chave.equals("Hora"))
							if (!Pattern.matches("'\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3,}'", valor)) {
								// System.err.println("VALOR "+valor);
								dadosAnomalos.add(data);
								System.err.println("HORA MAL FORMATADA");
								anomalia = true; // Define como anomalia se Hora estiver mal formatada
							} else if (chave.equals("Leitura")) {
								try {
									Float.parseFloat(valor);
								} catch (NumberFormatException e) {
									dadosAnomalos.add(data);
									System.err.println("LEITURA MAL FORMATADA");
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							}
					}
				}
				if (!anomalia) // Se não for uma anomalia, adicione aos dados corretos
					dadosCorretos.add(data);
			}
			/*
			 * }
			 * count++;
			 */
		}

		writeArrayListToFile(dadosCorretos, "DadosCorretosTemperatura.txt");
		writeArrayListToFile(dadosAnomalos, "DadosAnomalosTemperatura.txt");

		for (String data : dadosCorretos) {
			WriteToMySQL(com.mongodb.util.JSON.serialize(data));
			// TODO: Salvar data no ficheiro txt para backup
		}

		// System.out.println("\n\n\n\n A DETETAR OUTLIERS:\n\n");
		detetarOutliers(dadosCorretos);
	}

	private void detetarOutliers(ArrayList<String> dadosCorretos) {
		ArrayList<String> outliers = new ArrayList<String>();

		for (int i = 0; i < dadosCorretos.size(); i++) {
			// Calcula o IQR
			float Q1 = calculatePercentile(1);
			float Q3 = calculatePercentile(2);

			if (Q1 != Float.NEGATIVE_INFINITY && Q3 != Float.NEGATIVE_INFINITY) {
				float IQR = Q3 - Q1;

				// Define os limites
				float lowerLimit = Q1 - 1.5f * IQR;
				float upperLimit = Q3 + 1.5f * IQR;

				float temperatura = extrairValorLeitura(dadosCorretos.get(i));
				// Verifica se a temperatura é um outlier
				if (temperatura < lowerLimit || temperatura > upperLimit) {
					outliers.add(dadosCorretos.get(i));
					// como e outlier vai buscar o ultimo valido
					dadoSet.add(dadosCorretos.get(i));
					// System.out.println("Temperatura É um outlier: " + temperatura);
				} else {
					// Se não for um outlier, insere no conjunto de dados
					dadoSet.add(dadosCorretos.get(i));
					// System.out.println("Temperatura NÃO É um outlier: " + temperatura);
				}
			} else
				dadoSet.add(dadosCorretos.get(i));
		}

		// FAZER ALGO COM OS OUTLIERS
		/*
		 * System.out.println("\n\n\n\n\n OUTLIERS DETETADOS");
		 * for (String dadoOutlier : outliers)
		 * System.out.println(dadoOutlier);
		 */
		writeArrayListToFile(outliers, "DadosOutliersTemperatura.txt");
		writeHashSetToFile(dadoSet, "DadoSetTemperatura.txt");
	}

	public float calculatePercentile(float quartile) {
		int start = dadoSet.size() - OUTLIERS;
		if (start <= 0)
			return Float.NEGATIVE_INFINITY;
		else {
			int count = 0;
			ArrayList<String> temp = new ArrayList<String>();
			for (String data : dadoSet) {
				if (count >= start)
					temp.add(data);
				count++;
			}

			Collections.sort(temp, new Comparator<String>() {
				@Override
				public int compare(String s1, String s2) {
					float leitura1 = extrairValorLeitura(s1);
					float leitura2 = extrairValorLeitura(s2);
					return Float.compare(leitura1, leitura2);
				}
			});

			int tamanho = temp.size();
			if (quartile == 1) {
				if (OUTLIERS % 2 == 0)
					return (extrairValorLeitura(temp.get(tamanho / 4))
							+ extrairValorLeitura(temp.get((tamanho / 4) + 1))) / 2f;
				else
					return extrairValorLeitura(temp.get(tamanho / 4));
			} else {
				if (OUTLIERS % 2 == 0)
					return (extrairValorLeitura(temp.get(3 * tamanho / 4))
							+ extrairValorLeitura(temp.get((3 * tamanho / 4) + 1))) / 2f;
				else
					return extrairValorLeitura(temp.get(3 * tamanho / 4));
			}
		}
	}

	private float extrairValorLeitura(String data) {
		String[] partes = data.split(", ");
		for (String parte : partes)
			if (parte.startsWith("Leitura:")) {
				String valorLeitura = parte.substring("Leitura: ".length());
				return Float.parseFloat(valorLeitura);
			}
		return Float.NEGATIVE_INFINITY;
	}

	public void WriteToMySQL(String c) {
		String convertedjson = new String();
		convertedjson = c;
		String fields = new String();
		String values = new String();
		String SqlCommando = new String();
		String column_database = new String();
		fields = "";
		values = "";
		column_database = " ";
		String x = convertedjson.toString();
		String[] splitArray = x.split(",");
		for (int i = 0; i < splitArray.length; i++) {
			String[] splitArray2 = splitArray[i].split(":");
			if (i == 0)
				fields = splitArray2[0];
			else
				fields = fields + ", " + splitArray2[0];
			if (i == 0)
				values = splitArray2[1];
			else
				values = values + ", " + splitArray2[1];
		}
		fields = fields.replace("\"", "");
		SqlCommando = "Insert into " + sql_table_to + " (" + fields.substring(1, fields.length()) + ") values ("
				+ values.substring(0, values.length() - 1) + ");";
		// System.out.println(SqlCommando);
		try {
			documentLabel.append(SqlCommando.toString() + "\n");
		} catch (Exception e) {
			System.out.println(e);
		}
		try {
			Statement s = connTo.createStatement();
			int result = new Integer(s.executeUpdate(SqlCommando));
			s.close();
		} catch (Exception e) {
			System.out.println("Error Inserting in the database . " + e);
			System.out.println(SqlCommando);
		}
	}

	////////////////////////////////////////////////////////////// ESCREVER EM
	////////////////////////////////////////////////////////////// FICHEIRO PARA
	////////////////////////////////////////////////////////////// TESTE//////////////////////////////////

	public void writeArrayListToFile(ArrayList<String> dataList, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("JavaMysql/Anomalos/" + fileName))) {
			for (String data : dataList) {
				writer.write(data);
				writer.newLine(); // Adiciona uma nova linha após cada conjunto de dados
			}
			System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
		} catch (IOException e) {
			System.err.println("Erro ao escrever dados no arquivo: " + fileName);
			e.printStackTrace();
		}
	}

	public void writeHashSetToFile(Set<String> dataSet, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("JavaMysql/Anomalos/" + fileName))) {
			for (String data : dataSet) {
				writer.write(data);
				writer.newLine(); // Adiciona uma nova linha após cada conjunto de dados
			}
			System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
		} catch (IOException e) {
			System.err.println("Erro ao escrever dados no arquivo: " + fileName);
			e.printStackTrace();
		}
	}

	public static void writeMapToFile(HashMap<Integer, Integer> dataMap, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("JavaMysql/Anomalos/" + fileName))) {
			for (Integer key : dataMap.keySet()) {
				String data = "Sala " + key + ": " + dataMap.get(key);
				writer.write(data);
				writer.newLine(); // Adiciona uma nova linha após cada conjunto de dados
			}
			System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
		} catch (IOException e) {
			System.err.println("Erro ao escrever dados no arquivo: " + fileName);
			e.printStackTrace();
		}
	}

	/////////////////////////////////////////////////// MAIN////////////////////////////////////////////////////////
	public static void main(String[] args) {
		createWindow();
		try {
			Properties p = new Properties();
			p.load(new FileInputStream("JavaMysql/WriteMysql.ini"));
			sql_table_to = p.getProperty("sql_table_to");
			sql_database_connection_to = p.getProperty("sql_database_connection_to");
			sql_database_password_to = p.getProperty("sql_database_password_to");
			sql_database_user_to = p.getProperty("sql_database_user_to");
			mongo_user = p.getProperty("mongo_user");
			mongo_password = p.getProperty("mongo_password");
			mongo_address = p.getProperty("mongo_address");
			mongo_database = p.getProperty("mongo_database");
			mongo_authentication = p.getProperty("mongo_authentication");
			mongo_doors = p.getProperty("mongo_doors");
			mongo_temp1 = p.getProperty("mongo_temp1");
			mongo_temp2 = p.getProperty("mongo_temp2");
			mongo_replica = p.getProperty("mongo_replica");

		} catch (Exception e) {
			System.out.println("Error reading WriteMysql.ini file " + e);
			JOptionPane.showMessageDialog(null, "The WriteMysql inifile wasn't found.", "Data Migration",
					JOptionPane.ERROR_MESSAGE);
		}
		new WriteMysql().connectToMongo();
		// new WriteMysql().connectDatabase_to();
		new WriteMysql().ReadData();
	}
}