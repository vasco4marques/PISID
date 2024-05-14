//(c) ISCTE-IUL, Pedro Ramos, 2022

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.sql.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

import org.bson.*;

import com.mongodb.*;
import com.mongodb.client.MongoIterable;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.util.JSON;

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
public class WriteMysql {

	// Objetos Mongo
	static MongoClient mongoClient;
	static DB db;
	static DBCollection colDoors;
	static DBCollection colTemp1;
	static DBCollection colTemp2;
	static DBCollection lastInsertedIds;

	static MongoDatabase db2;

	// Objeto SQL
	static Connection connTo;

	static JTextArea documentLabel = new JTextArea("\n");

	// Dados do SQL do ficheiro ini
	static String sql_database_connection_to = new String();
	static String sql_database_password_to = new String();
	static String sql_database_user_to = new String();
	// static String sql_table_to = new String();

	// SQL do stor
	static String sql_storDatabase_connection_to = new String();
	static String sql_storDatabase_password_to = new String();
	static String sql_storDatabase_user_to = new String();

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
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	HashMap<Integer, Integer> salasMap = new HashMap<>();
	private final static int OUTLIERS = 16;
	private static int MAXRATOS;
	private static int MAXTIMEPARADOS;
	private final static int TIMELOOP = 3000;

	// janela para aparecer informacoes de mongo o sql
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

	////////////////////////////////////////////// CONECOES A MONGO E BASE DE
	////////////////////////////////////////////// DADOS//////////////////////////////////////////
	// Conectar a base de dados
	public void connectDatabase_to() {
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			connTo = DriverManager.getConnection(sql_database_connection_to, sql_database_user_to, "");
			documentLabel.append("SQl Connection:" + sql_database_connection_to + "\n");
			documentLabel
					.append("Connection To MariaDB Destination " + sql_database_connection_to + " Suceeded" + "\n");
		} catch (Exception e) {
			System.out.println("Mysql Server Destination down, unable to make the connection. " + e);
		}

		try {
			Class.forName("org.mariadb.jdbc.Driver");
			connTo = DriverManager.getConnection(sql_storDatabase_connection_to, sql_storDatabase_user_to,
					sql_storDatabase_password_to);
			documentLabel.append("SQl Connection:" + sql_storDatabase_connection_to + "\n");
			documentLabel
					.append("Connection To MariaDB Destination " + sql_storDatabase_connection_to + " Suceeded" + "\n");

		} catch (Exception e) {
			System.out.println("Stor Mysql Server Destination down, unable to make the connection. " + e);
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
		db2 = mongoClient.getDatabase(mongo_database);
		// 3 coleções que precisas
		colDoors = db.getCollection(mongo_doors);
		colTemp1 = db.getCollection(mongo_temp1);
		colTemp2 = db.getCollection(mongo_temp2);
		lastInsertedIds = db.getCollection("lastInsertedIds");

	}

	private void writeInMongoBackupValue(String colIdUpdate, int newValue) {
		Document filter = new Document("name", colIdUpdate);
		Document update = new Document("$inc", new Document("id", 1));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
				.returnDocument(com.mongodb.client.model.ReturnDocument.AFTER);
		Document sequenceDocument = db2.getCollection("lastInsertedIds").findOneAndUpdate(filter, update, options);
		if (sequenceDocument == null) {
			db2.getCollection("lastInsertedIds")
					.insertOne(new Document("name", colIdUpdate).append("lastInsertedID", newValue));
		}

	}

	private Map<String, Integer> readLastProcessedIds() {
		Map<String, Integer> ids = new HashMap<>();
		try (DBCursor cursor = lastInsertedIds.find()) {
			while (cursor.hasNext()) {
				DBObject nextElement = cursor.next();
				ids.put((String) (nextElement.get("name")), (int) (nextElement.get("id")));
			}
		}
		return ids;
	}

	// Lê a coleção dada como parâmetro e retorna uma lista de DBObjects
	// Para leres estes objetos podes usar o .get(key) que ele retorna o value
	public List<DBObject> readFromMongo(DBCollection col, String collectionName) {
		List<DBObject> results = new ArrayList<>();
		Map<String, Integer> lastIds = readLastProcessedIds();

		// Cria uma query para ter apenas os documentos que têm um _id maior que o
		// último guardado no ficheiro de backup
		BasicDBObject query = new BasicDBObject();
		if (lastIds.containsKey(collectionName)) {
			query.put("id", new BasicDBObject("$gt", lastIds.get(collectionName)));
		} else {
			query.put("id", new BasicDBObject("$gt", 0));
		}

		try (DBCursor cursor = col.find(query)) {
			while (cursor.hasNext()) {
				results.add(cursor.next());
			}
		}
		return results;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////// FUNCAO PRINCIPAL DE DE X EM X
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SEGUNDOS
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// VAI
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// BUSCAR
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// AO
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// MONGO
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// E
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// ENVIA
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// PARA
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// O
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SQL//////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void ReadData() {
		System.out.println("Vou começar a leitura");

		// Começa e acaba quando
		// Hora ser 2000-01-01 00:00:00.000
		// e se sala origem e destino for 0
		boolean comecar = false;

		while (true) {

			MAXRATOS = numMaxRatos();
			MAXTIMEPARADOS = tempoParadosPorSala();

			float start = System.nanoTime();

			ArrayList<String> dateListRatos = new ArrayList<>();

			List<DBObject> portas = readFromMongo(colDoors, mongo_doors);
			System.out.println("ports " + portas.size());

			// List<DBCollection> tempCollections = getAllTempCollections();
			// List<List<DBObject>> tempData = new ArrayList<List<DBObject>>();
			// for (DBCollection col : tempCollections) {
			// tempData.add(readFromMongo(col, col.getName()));
			// }
			// System.out.println("Dados de temperaturas retirados do mongo");

			for (DBObject sensor : portas) {
				String data = sensor.toString();
				// Se o registo que existe tiver a data o começar inverte (começa a false logo
				// passa para true)
				if (data.contains("2000-01-01 00:00:00")) {
					System.out.println("Encontrei documento com esta data");
					comecar = !comecar;
					// Se o começar for true vamos começar com os mapas dos ratos
					// Caso não seja true quer dizer que é o segunda vez que ele encontra este
					// registo logo a experiência acabou e para este ciclo
					if (comecar)
						salasMap.put(1, inicialNumRatos());
					else
						break;

					// Continue serve para saltar para a prox iteraçãp
					continue;
				}
				// Caso a experiencia tenha começado na verificação interior, depois de saltar o
				// registo com aquela data específica adicionamos os datos a lista de dados
				if (comecar) {
					dateListRatos.add(data);
				}

				// if (comecar) {
				// System.out.println("Como começar está a true vou adicionar data a lista de
				// ratos");
				// int salaOrigem = (int) sensor.get("SalaOrigem");
				// int salaDestino = (int) sensor.get("SalaDestino");
				// if (!salasMap.containsKey(salaOrigem))
				// salasMap.put(salaOrigem, 0);
				// if (!salasMap.containsKey(salaDestino))
				// salasMap.put(salaDestino, 0);
				// }
			}

			// List<ArrayList<String>> tempsStrings = new ArrayList<>();
			// for (List<DBObject> col : tempData) {
			// ArrayList<String> tempsToStringTemporaria = new ArrayList<>();
			// for (DBObject temp : col) {
			// tempsToStringTemporaria.add(temp.toString());
			// }
			// tempsStrings.add(tempsToStringTemporaria);
			// }

			// List<ArrayList<String>> tempsValidadas = new ArrayList<>();
			// for (ArrayList<String> toBeValidated : tempsStrings) {
			// tempsValidadas.add(validarFormatosTemperatura(toBeValidated));
			// }

			// Criar iteradores para cada uma das listas dentro do tempValidadas
			// while loop enquanto qualquer iterador tiver next
			// if iterador x tiver next retirar da sua lista e meter na final

			// ArrayList<String> tempsMisturadas = new ArrayList<>();
			// while (existemElemnetos(tempsValidadas)) {
			// for (ArrayList<String> listas : tempsValidadas)
			// if (!listas.isEmpty())
			// tempsMisturadas.add(listas.remove(0).toString());
			// }
			// System.out.println("TempsMisturadas" + tempsMisturadas.size());

			// writeArrayListToFile(dateListRatos, "DadosMongoSalas.txt");
			// System.out.println("escrevi os ratos");t

			// writeArrayListToFile(dateListTemperatura, "DadosMongoTemperatura.txt");
			// detetarOutliers(tempsMisturadas);

			validarFormatosSalas(dateListRatos);

			float end = System.nanoTime();
			float time = (end - start) / 1000000000;

			if (time >= 0 && time <= TIMELOOP) {
				try {
					Thread.sleep(3000 - (long) time);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	private boolean existemElemnetos(List<ArrayList<String>> tempsValidadas) {
		boolean vazio = false;
		for (ArrayList<String> temps : tempsValidadas)
			vazio = !temps.isEmpty();
		return vazio;
	}

	public List<DBCollection> getAllTempCollections() {
		List<DBCollection> tempList = new ArrayList<DBCollection>();
		MongoIterable<String> lista = db2.listCollectionNames();
		for (String a : lista) {
			if (a.contains("sensoresTemp")) {
				tempList.add(db.getCollection(a));
			}
		}
		return tempList;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////// FUNCOES RELACIONADAS COM AS
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SALAS////////////////////////////////////////////////////////////////////////////////////////////

	private void validarFormatosSalas(ArrayList<String> dateListRatos) {

		ArrayList<String> dadosAnomalos = new ArrayList<String>();
		ArrayList<String> dadosCorretos = new ArrayList<String>();
		for (String data : dateListRatos) {
			boolean anomalia = false; // Variável para verificar se é uma anomalia
			if (!data.contains("Hora") || !data.contains("SalaDestino") || !data.contains("SalaOrigem")) {
				System.out.println("Formato errado logo é anomalo " + data);
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
						if (chave.equals("\"Hora\"")) {
							if (!Pattern.matches("\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3,}\"", valor)) {

								System.err.println("Data mal formatada" + valor);
								dadosAnomalos.add(data);
								// System.err.println("HORA MAL FORMATADA");
								anomalia = true; // Define como anomalia se Hora estiver mal formatada

							} else if (!isValidDate(valor)) {
								System.err.println("Data invalida" + valor);

								dadosAnomalos.add(data);
								anomalia = true;

							} else if (chave.equals("SalaDestino")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {

									System.err.println("Sala Destino MAL FORMATADA");
									dadosAnomalos.add(data);
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							} else if (chave.equals("SalaOrigem")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {
									System.err.println("Sala Destino MAL FORMATADA");
									dadosAnomalos.add(data);
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							}
						}
					}
				}
				if (!anomalia) {
					// Se não for uma anomalia, adicione aos dados corretos
					System.out.println("Tudo bem com este " + data);
					dadosCorretos.add(data);
				}

			}
		}

		// writeArrayListToFile(dadosCorretos, "DadosCorretosSalas.txt");
		// writeArrayListToFile(dadosAnomalos, "DadosAnomalosSalas.txt");

		moverRatos(dadosCorretos, dadosAnomalos);
	}

	private boolean isValidDate(String valor) {
		// System.out.println("Validating value " + valor + "\n");
		String[] fullData = valor.split(" ");

		String[] data = fullData[0].replace("\"", "").split("-"); // ano - mês - dia
		Boolean leapYear = false;// Ano bissexto
		if (Integer.parseInt(data[0]) % 400 == 0 && Integer.parseInt(data[0]) % 100 == 0) {
			leapYear = true;
		} else if (Integer.parseInt(data[0]) % 100 != 0 && Integer.parseInt(data[0]) % 4 == 0) {
			leapYear = true;
		}

		if (data[1] == "02" && leapYear && Integer.parseInt(data[2]) > 29) {
			return false;
		} else if (data[1] == "02" && !leapYear && Integer.parseInt(data[2]) > 28) {
			return false;
		}

		if (Integer.parseInt(data[1]) <= 7) {
			if (Integer.parseInt(data[1]) % 2 == 0) {
				if (Integer.parseInt(data[2]) > 30)
					return false;
			} else {
				if (Integer.parseInt(data[2]) > 31)
					return false;
			}
		} else {
			if (Integer.parseInt(data[1]) % 2 == 0) {
				if (Integer.parseInt(data[2]) > 31)
					return false;
			} else {
				if (Integer.parseInt(data[2]) > 30)
					return false;
			}
		}
		return true;
	}

	private void moverRatos(ArrayList<String> dadosCorretos, ArrayList<String> dadosAnomalos) {
		System.out.println("Quantos dados correto chegaram" + dadosCorretos.size());
		System.out.println("Quantos dados errados chegaram" + dadosAnomalos.size());
		for (String data : dadosCorretos) {

			int salaOrigem = extractRoom(data, "SalaOrigem");
			int salaDestino = extractRoom(data, "SalaDestino");

			int quantidadeDestino = salasMap.get(salaDestino);
			int quantidadeOrigem = salasMap.get(salaOrigem);

			if (quantidadeOrigem - 1 < 0) {
				System.out.println("A sala " + salaOrigem + " tem valores negativos. Algo de errado ocorreu.");
				dadosAnomalos.add(data);
			} else {

				if (quantidadeDestino + 1 >= MAXRATOS) {
					System.out.println("Lotado e vou inserir alerta");
					System.out
							.println("A sala " + salaDestino + " já está lotada. Não é possível adicionar mais ratos.");
					salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
					salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
					writeToMySQL(data, "medicoes_passagens");
					writeAlertaToMySQL(data, "MaxRatos", "Numero de ratos excedidos na sala " + salaDestino);
					break;
				} else {
					System.out.println("Tudo bem e vou inserir portas");
					salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
					salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
					writeToMySQL(data, "medicoes_passagens");
				}
			}
		}
	}

	public int extractRoom(String data, String key) {
		System.out.println("Extrair room");
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

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////// FUNCOES RELACIONADAS COM AS
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// TEMPERATURAS
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// ////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public ArrayList<String> validarFormatosTemperatura(ArrayList<String> dateListTemperatura) {

		ArrayList<String> dadosAnomalos = new ArrayList<String>();
		ArrayList<String> dadosCorretos = new ArrayList<String>();
		for (String data : dateListTemperatura) {
			boolean anomalia = false; // Variável para verificar se é uma anomalia
			if (!data.contains("Hora") || !data.contains("Leitura")) {
				dadosAnomalos.add(data);
				// System.err.println("HORA OU LEITURA NÃO ENCONTRADOS");
				anomalia = true; // Define como anomalia se Hora ou Leitura não forem encontrados
			}

			if (!anomalia) { // Se não for uma anomalia
				String[] campos = data.split(", ");
				for (String campo : campos) {
					String[] partes = campo.split(": ");
					if (partes.length == 2) {
						String chave = partes[0].trim();
						String valor = partes[1].trim();
						if (chave.equals("\"Hora\"")) {
							if (!Pattern.matches("\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3,}\"", valor)) {
								dadosAnomalos.add(data);
								System.err.println("Data MAL FORMATADA " + valor);
								anomalia = true; // Define como anomalia se Hora estiver mal formatada

							} else if (!isValidDate(valor)) {
								System.err.println("Data inválida");
								dadosAnomalos.add(data);
								anomalia = true;

							} else if (chave.equals("\"Leitura\"")) {
								try {
									Float.parseFloat(valor);
								} catch (NumberFormatException e) {
									dadosAnomalos.add(data);
									System.err.println("LEITURA MAL FORMATADA");
									anomalia = true;
								}
							}
						}
					}
				}
				if (!anomalia) {
					// System.out.println("ESTE DADO ESTAVA BEM");
					dadosCorretos.add(data);
				}
			}
		}

		// writeArrayListToFile(dadosCorretos, "DadosCorretosTemperatura.txt");
		// writeArrayListToFile(dadosAnomalos, "DadosAnomalosTemperatura.txt");
		return dadosCorretos;
	}

	private void detetarOutliers(ArrayList<String> dadosCorretos) {
		System.out.println("Vou ver outliers");
		System.out.println(dadosCorretos.isEmpty());
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
					System.out.println("Foi detetado como OUTILIER: " + dadosCorretos.get(i));
					// System.out.println("Temperatura É um outlier: " + temperatura);
				} else {
					// Se não for um outlier, insere no conjunto de dados
					System.out.println("Não e outllier: " + dadosCorretos.get(i));
					dadoSet.add(dadosCorretos.get(i));
					writeToMySQL(dadosCorretos.get(i), "medicoes_temperatura");
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
		// writeArrayListToFile(outliers, "DadosOutliersTemperatura.txt");
		// writeHashSetToFile(dadoSet, "DadoSetTemperatura.txt");
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
				int index = tamanho / 4;
				if (OUTLIERS % 2 == 0)
					return (extrairValorLeitura(temp.get(index)) + extrairValorLeitura(temp.get(index + 1))) / 2f;
				else
					return extrairValorLeitura(temp.get(index));
			} else {
				int index = 3 * tamanho / 4;
				if (OUTLIERS % 2 == 0)
					return (extrairValorLeitura(temp.get(index)) + extrairValorLeitura(temp.get(index + 1))) / 2f;
				else
					return extrairValorLeitura(temp.get(index));
			}
		}
	}

	private float extrairValorLeitura(String data) {
		String[] partes = data.split(", ");
		for (String parte : partes) {
			if (parte.startsWith("\"Leitura\" :")) {
				String valorLeitura = parte.substring("\"Leitura\" : ".length());
				return Float.parseFloat(valorLeitura);
			}
		}
		return Float.NEGATIVE_INFINITY;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////// FUNCAO PARA ESCREVER E LER NO
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SQL////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void writeToMySQL(String c, String tabela) {
		String SqlCommando = "";
		String hora = "";
		String leitura = "";
		String sensor = "";
		String salaOrigem = "";
		String salaDestino = "";
		int id = -1;

		switch (tabela) {
			case "medicoes_temperatura":
				hora = extractValue(c, "Hora");
				if (hora.length() == 19) {
					hora += ".075649";
				}
				leitura = extractValue(c, "Leitura");
				sensor = extractValue(c, "Sensor");
				id = idExperienciaFromSQL();
				SqlCommando = "Insert into medicoes_temperatura" + " (" + "id_ex, hora, leitura, sensor" + ") values ("
						+ id
						+ ",'" + hora + "', " + leitura + ", " + sensor + ");";

				break;

			case "medicoes_passagens":
				hora = extractValue(c, "Hora");
				if (hora.length() == 19) {
					hora += ".075649";
				}
				salaOrigem = extractValue(c, "SalaOrigem");
				salaDestino = extractValue(c, "SalaDestino");
				id = idExperienciaFromSQL();
				SqlCommando = "Insert into medicoes_passagens" + " (" + "id_ex, hora, sala_origem, sala_destino"
						+ ") values (" +
						id + ", '" + hora + "', " + salaOrigem + ", " + salaDestino + ");";
				break;
			default:
				break;
		}

		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				documentLabel.append(SqlCommando.toString() + "\n");
				Statement s = connTo.createStatement();
				int result = s.executeUpdate(SqlCommando);
				System.out.println("Inseri " + result + "\n");
				if (tabela.equals("medicoes_passagens")) {
					System.out.println("vou dar update ao sensor das portas");
					writeInMongoBackupValue("sensoresPortas", id);
				} else {

					if (sensor.equals("1"))
						writeInMongoBackupValue("sensoresTemp1", id);
					else
						writeInMongoBackupValue("sensoresTemp2", id);
				}
				s.close();
				commandExecutedSuccessfully = true; // Define como verdadeiro se a execução foi bem-sucedida
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
				}

				System.out.println("Error Inserting in the database . " + e);
				// Em caso de falha, aguarde antes de tentar novamente
				try {
					Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public void writeAlertaToMySQL(String c, String tipo_alerta, String mensagem) {

		String hora = extractValue(c, "Hora") == null ? "NULL" : extractValue(c, "Hora");
		if (!hora.equals("NULL") && hora.length() == 19) {
			hora += ".075649";
		}
		String salaOrigem = extractValue(c, "SalaOrigem") == null ? "NULL" : extractValue(c, "SalaOrigem");
		String salaDestino = extractValue(c, "SalaDestino") == null ? "NULL" : extractValue(c, "SalaDestino");
		int id = idExperienciaFromSQL();
		String leitura = extractValue(c, "Leitura") == null ? "NULL" : extractValue(c, "Leitura");
		String sensor = extractValue(c, "Sensor") == null ? "NULL" : extractValue(c, "Sensor");
		String SqlCommando = "Insert into alerta" + " ("
				+ "id_ex, hora_real, sala, sensor, leitura, tipo_alerta, mensagem"
				+ ") values (" +
				id + ", '" + hora + "', " + salaDestino + ", " + sensor + ", " + leitura + ", " + tipo_alerta + ", "
				+ mensagem + " );";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				documentLabel.append(SqlCommando.toString() + "\n");
				Statement s = connTo.createStatement();
				int result = s.executeUpdate(SqlCommando);
				System.out.println("Inseri " + result + "\n");

				s.close();
				commandExecutedSuccessfully = true; // Define como verdadeiro se a execução foi bem-sucedida
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
				}

				System.out.println("Error Inserting in the database . " + e);
				// Em caso de falha, aguarde antes de tentar novamente
				try {
					Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public int idExperienciaFromSQL() {
		int maxIdExperiencia = -1;
		String SqlCommando = "SELECT MAX(id_ex) FROM experiencia;";
		try {
			Statement s = connTo.createStatement();
			ResultSet rs = s.executeQuery(SqlCommando);
			if (rs.next()) {
				maxIdExperiencia = rs.getInt(1); // Obtém o valor máximo da coluna id_experiencia
				// Use o valor maxIdExperiencia conforme necessário
				// System.out.println("Maior valor de id_experiencia: " + maxIdExperiencia);
			}
			s.close();
		} catch (Exception e) {
			System.out.println("Erro ao buscar o maior valor de id_experiencia: " + e);
		}
		return maxIdExperiencia;
	}

	public int experienciaAtiva() {
		int idExperiencia = -1;
		String SqlCommando = "SELECT id FROM experiencia WHERE data_hora_fim IS NULL ORDER BY data_hora_inicio DESC LIMIT 1;";
		try {
			Statement s = connTo.createStatement();
			ResultSet rs = s.executeQuery(SqlCommando);
			if (rs.next()) {
				idExperiencia = rs.getInt(1);
			}
			s.close();
		} catch (Exception e) {
			idExperiencia = -1;
		}
		return idExperiencia;
	}

	public int inicialNumRatos() {
		int numRatos = -1;
		String SqlCommando = "SELECT num_ratos FROM experiencia WHERE data_hora_fim IS NULL ORDER BY data_hora_inicio DESC LIMIT 1;";
		try {
			Statement s = connTo.createStatement();
			ResultSet rs = s.executeQuery(SqlCommando);
			if (rs.next()) {
				numRatos = rs.getInt(1);
			}
			s.close();
		} catch (Exception e) {
			numRatos = -1;
		}
		return numRatos;
	}

	public int numMaxRatos() {
		int numMaxRatos = -1;
		String SqlCommando = "SELECT LimiteRatosNumaSala AS maximo_de_ratos_por_sala FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		try {
			Statement s = connTo.createStatement();
			ResultSet rs = s.executeQuery(SqlCommando);
			if (rs.next()) {
				numMaxRatos = rs.getInt(1);
			}
			s.close();
		} catch (Exception e) {
			numMaxRatos = -1;
		}
		return numMaxRatos;
	}

	public int tempoParadosPorSala() {
		int tempo = -1;
		String SqlCommando = "SELECT LimiteRatosNumaSala AS maximo_de_ratos_por_sala FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		try {
			Statement s = connTo.createStatement();
			ResultSet rs = s.executeQuery(SqlCommando);
			if (rs.next()) {
				tempo = rs.getInt(1);
			}
			s.close();
		} catch (Exception e) {
			tempo = -1;
		}
		return tempo;
	}

	public String extractValue(String data, String field) {
		int index = data.indexOf("\"" + field + "\"");
		if (index != -1) {
			index = data.indexOf(":", index + field.length() + 2); // Adiciona 2 para pular os dois pontos e um espaço
			if (index != -1) {
				int start = index + 1;
				while (start < data.length()
						&& (Character.isWhitespace(data.charAt(start)) || data.charAt(start) == '"')) {
					start++;
				}
				int end = start;
				while (end < data.length() && data.charAt(end) != ',' && data.charAt(end) != '}') {
					end++;
				}
				return data.substring(start, end).trim().replace("\"", "");
			}
		}
		return null;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////// ESCREVER EM
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// FICHEIRO
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// PARA
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// TESTE//////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void writeArrayListToFile(ArrayList<String> dataList, String fileName) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("JavaMysql/Anomalos/" + fileName))) {
			for (String data : dataList) {
				writer.write(data);
				writer.newLine(); // Adiciona uma nova linha após cada conjunto de dados
			}
			// System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
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
			// System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
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
			// System.out.println("Dados gravados com sucesso no arquivo: " + fileName);
		} catch (IOException e) {
			System.err.println("Erro ao escrever dados no arquivo: " + fileName);
			e.printStackTrace();
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////// MAIN//////////////////////////////////////////////////////////////////////////////////////////////////////

	public static void main(String[] args) {
		createWindow();
		try {
			Properties p = new Properties();
			// LINHA PARA OS RESTANTES
			// p.load(new FileInputStream("JavaMysql/WriteMysql.ini"));
			// Linha para funcionar no VASCO
			p.load(new FileInputStream("E:\\3ºAno\\2ºSemestre\\PISID\\PISID\\JavaMysql\\WriteMysql.ini"));
			// sql_table_to = p.getProperty("sql_table_to");
			sql_database_connection_to = p.getProperty("sql_database_connection_to");
			sql_database_password_to = p.getProperty("sql_database_password_to");
			sql_database_user_to = p.getProperty("sql_database_user_to");

			sql_storDatabase_connection_to = p.getProperty("sql_storDatabase_connection_to");
			sql_storDatabase_password_to = p.getProperty("sql_storDatabase_password_to");
			sql_storDatabase_user_to = p.getProperty("sql_storDatabase_user_to");

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
		new WriteMysql().connectDatabase_to();
		new WriteMysql().ReadData();

		// while (true) {
		// String sql = "Insert into medicoes_passagens( id_ex, sala_origem,
		// sala_destino) values ("
		// + "3" + ", " + Math.random() * 10 + ", " + Math.random() * 10 + ");";
		// boolean commandExecutedSuccessfully = false;
		// while (!commandExecutedSuccessfully) {
		// try {
		// documentLabel.append(sql.toString() + "\n");
		// Statement s = connTo.createStatement();
		// int result = s.executeUpdate(sql);
		// s.close();
		// commandExecutedSuccessfully = true; // Define como verdadeiro se a execução
		// foi bem-sucedida
		// } catch (Exception e) {
		// new WriteMysql().connectDatabase_to();
		// System.out.println("Error Inserting in the database . " + e);
		// System.out.println(sql);
		// // Em caso de falha, aguarde antes de tentar novamente
		// try {
		// Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
		// } catch (InterruptedException ex) {
		// ex.printStackTrace();
		// }
		// }
		// }
		// try {
		// Thread.sleep(5000);
		// } catch (InterruptedException ex) {
		// ex.printStackTrace();
		// }
		// }
	}
}