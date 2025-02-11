//(c) ISCTE-IUL, Pedro Ramos, 2022

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.sql.*;
import java.sql.Date;

import javax.swing.*;
import java.time.ZoneOffset;
import java.awt.event.*;
import java.awt.*;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

import org.bson.*;
import java.time.LocalDateTime;

import com.mongodb.*;
import com.mongodb.client.MongoIterable;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.util.JSON;

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
public class WriteMysql {

	private static final DecimalFormat dfZero = new DecimalFormat("0.00");

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
	static Connection connToStor;

	static JTextArea documentLabel = new JTextArea("\n");

	// Dados do SQL do ficheiro ini
	static String sql_database_connection_to = new String();
	static String sql_database_password_to = new String();
	static String sql_database_user_to = new String();
	// static String sql_table_to = new String();

	// SQL do stor
	static String sql_maze_database_connection_to = new String();
	static String sql_maze_database_password_to = new String();
	static String sql_maze_database_user_to = new String();

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
	private float startRatosParados;
	private boolean haviaDados = true;
	private boolean alertaInseridoRatos = true;
	private final static int TIMELOOP = 3000;
	private static int RATOSALERTSTART;
	private static int TEMPOALERTASRATOS;
	private static float TEMPOALERTASRATOSAUX;
	private static int NUMINICIALRATOS;
	private static float last_temperature_alert = 0;
	private static long last_timestamp_alert = 0;
	private HashMap<Integer, LinkedList<Integer>> caminhos = new HashMap<Integer, LinkedList<Integer>>();
	private String lastInsertedRat = "";

	// TIPOS DE ALERTAS
	private static String RATOSPARADOS = "RatoParadoLotTime";
	private static String RatosQuaseMax = "RatosQuaseMax";
	private static String RatosMAX = "RatosMax";
	private static String TemperaturaQuaseMax = "TemperaturaQuaseMax";
	private static String TemperaturaMax = "TemperaturaChegouMax";
	private static String TemperaturaQuaseMin = "TemperaturaQuaseMin";
	private static String TemperaturaMin = "TemperaturaChegouMin";

	// TODO - 2. Leituras muito grandes passar para que tenham máximo de casas
	// deciamis ( FALTA TESTAR )
	// TODO - 3. Testar multiplas experiências seguidas

	// Função principal do nosso código
	public void ReadData() {
		System.out.println("Vou começar a leitura");

		// Começa e acaba quando
		// Hora ser 2000-01-01 00:00:00.000
		// e se sala origem e destino for 0
		boolean comecar = false;

		while (true) {
			MAXRATOS = numMaxRatosSala();

			float start = System.nanoTime();
			if (alertaInseridoRatos) {
				TEMPOALERTASRATOSAUX = System.nanoTime();
			}

			if (haviaDados) {
				startRatosParados = System.nanoTime();
			}

			ArrayList<String> dateListRatos = new ArrayList<>();

			List<DBObject> portas = readFromMongo(colDoors, mongo_doors);
			System.out.println("ports " + portas.size());

			List<DBCollection> tempCollections = getAllTempCollections();
			List<List<DBObject>> tempData = new ArrayList<List<DBObject>>();
			for (DBCollection col : tempCollections) {
				tempData.add(readFromMongo(col, col.getName()));
			}

			System.out.println("Dados de temperaturas retirados do mongo");
			// Só pode ir buscar dados se houver experiência ativa no sql (podemos inserir
			// uma experiência e ter tempo para ir buscar a proxima)
			if (idExperienciaFromSQL() != -1) {

				for (DBObject sensor : portas) {
					String data = sensor.toString();
					// Se o registo que existe tiver a data o começar inverte (começa a falselogo
					// passa para true)
					if (data.contains("\"SalaOrigem\" : 0 , \"SalaDestino\" : 0")) {
						System.out.println("Found this data");
						comecar = !comecar;
						// Se o começar for true vamos começar com os mapas dos ratos
						// Caso não seja true quer dizer que é o segunda vez que ele encontra este
						// registo logo a experiência acabou e para este ciclo
						if (comecar) {
							salasMap = new HashMap<>();
							caminhos.clear();
							construirCaminhos();
							popularMedicoes();
							NUMINICIALRATOS = inicialNumRatos();
							if (NUMINICIALRATOS != -1)
								salasMap.put(1, NUMINICIALRATOS);
						} else
							break;

						// Continue serve para saltar para a prox iteraçãp
						continue;
					}
					// Caso a experiencia tenha começado na verificação interior, depois de saltar o
					// registo com aquela data específica adicionamos os datos a lista de dadosif
					// (comecar) {
					if (comecar) {
						// System.out.println("tamanho dos dados entre a data " +
						dateListRatos.add(data);
					}

					if (comecar) {
						// System.out.println("Como começar está a true vou adicionar data a lista
						// de ratos");
						int salaOrigem = (int) sensor.get("SalaOrigem");
						int salaDestino = (int) sensor.get("SalaDestino");
						// System.err.println("Sala origem para inserir no mapa " + salaOrigem);
						// System.err.println("Sala destino para inserir no mapa " + salaDestino);

						if (!salasMap.containsKey(salaOrigem))
							salasMap.put(salaOrigem, 0);
						if (!salasMap.containsKey(salaDestino))
							salasMap.put(salaDestino, 0);
					}
				}
			}
			System.out.println("Dados das portas todos adicionados");

			// TODO - Variavel no fim do for que atualiza tipo var = sensor.ToString().
			// If comecar (if data atual - data passada > tempo maximo parado)
			// Acabou a experiencia porque os ratos ficaram parado

			List<ArrayList<String>> tempsStrings = new ArrayList<>();
			for (List<DBObject> col : tempData) {
				ArrayList<String> tempsToStringTemporaria = new ArrayList<>();
				for (DBObject temp : col) {
					tempsToStringTemporaria.add(temp.toString());
				}
				tempsStrings.add(tempsToStringTemporaria);
			}

			List<ArrayList<String>> tempsValidadas = new ArrayList<>();
			for (ArrayList<String> toBeValidated : tempsStrings) {
				tempsValidadas.add(validarFormatosTemperatura(toBeValidated));
			}

			// Criar iteradores para cada uma das listas dentro do tempValidadas
			// while loop enquanto qualquer iterador tiver next
			// if iterador x tiver next retirar da sua lista e meter na final

			ArrayList<String> tempsMisturadas = new ArrayList<>();
			while (existemElementos(tempsValidadas))
				for (ArrayList<String> listas : tempsValidadas)
					if (!listas.isEmpty())
						tempsMisturadas.add(listas.remove(0).toString());

			// System.out.println("TempsMisturadas" + tempsMisturadas.size());

			// writeArrayListToFile(dateListRatos, "DadosMongoSalas.txt");
			// System.out.println("escrevi os ratos");t

			// writeArrayListToFile(dateListTemperatura, "DadosMongoTemperatura.txt");
			detetarOutliers(tempsMisturadas);
			System.out.println("escrevi os outliers");

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
	// Funções auxiliares à função principal

	private void construirCaminhos() {
		String command = "Select * FROM corredor";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				PreparedStatement statement = connToStor.prepareStatement(command);
				ResultSet result = statement.executeQuery();

				while (result.next()) {
					String roomA = result.getString("salaa");
					String roomB = result.getString("salab");
					if (!caminhos.containsKey(Integer.parseInt(roomA))) {
						LinkedList<Integer> list = new LinkedList<Integer>();
						list.add(Integer.parseInt(roomB));
						caminhos.put(Integer.parseInt(roomA), list);
					} else {
						LinkedList<Integer> list = caminhos.get(Integer.parseInt(roomA));
						list.add(Integer.parseInt(roomB));
						caminhos.put(Integer.parseInt(roomA), list);
					}
				}
				result.close();
				statement.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				System.out.println("Error Selecting from the database . " + e);
				System.out.println(command);
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
				}

				System.out.println("Error Inserting in the database normal value. " + e);
				// Em caso de falha, aguarde antes de tentar novamente
				try {
					Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public boolean existeCaminho(int roomA, int roomB) {
		try {
			if (caminhos.containsKey(roomA)) {
				LinkedList<Integer> list = caminhos.get(roomA);
				if (list.contains(roomB)) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean existemElementos(List<ArrayList<String>> tempsValidadas) {
		boolean vazio = false;
		for (ArrayList<String> temps : tempsValidadas)
			vazio = !temps.isEmpty();
		return vazio;
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
		System.err.println("Vamos mover os ratos: " + dadosCorretos.size());
		boolean inserted = false;
		// System.out.println("Quantos dados correto chegaram" + dadosCorretos.size());
		// System.out.println("Quantos dados errados chegaram" + dadosAnomalos.size());
		for (String data : dadosCorretos) {

			int salaOrigem = extractRoom(data, "SalaOrigem");
			int salaDestino = extractRoom(data, "SalaDestino");

			int quantidadeDestino = salasMap.get(salaDestino);
			int quantidadeOrigem = salasMap.get(salaOrigem);

			if (!existeCaminho(salaOrigem, salaDestino)) {
				dadosAnomalos.add(data);
			} else {
				if (quantidadeOrigem - 1 < 0) {
					// System.out.println("A sala " + salaOrigem + " tem valores negativos. Algo de
					// errado ocorreu.");
					dadosAnomalos.add(data);
				} else {
					RATOSALERTSTART = numRatosMinimoParaAlerta();
					MAXRATOS = numMaxRatosSala();
					if (MAXRATOS != -1 && quantidadeDestino + 1 == MAXRATOS) {
						// System.out.println("Lotado e vou inserir alerta");
						// System.out.println("A sala " + salaDestino + " já está lotada. Não é possível
						// adicionar mais ratos.");
						salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
						salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
						writeToMySQL(data, "medicoes_passagens");
						inserted = true;
						if (hasMaxTimePassed(data, lastInsertedRat)) {
							writeAlertaToMySQL(data, RATOSPARADOS, "Ratos ficaram parados durante muito tempo");
							inserirDataFimExperiencia(idExperienciaFromSQL());
						}
						updateSala(salaDestino, quantidadeDestino + 1);
						updateSala(salaOrigem, quantidadeOrigem - 1);
						writeAlertaToMySQL(data, RatosMAX,
								"Numero de ratos numa sala chegou ao máximo " + salaDestino);
						inserirDataFimExperiencia(idExperienciaFromSQL());
						break;
					} else if (RATOSALERTSTART != -1 && MAXRATOS != -1 && quantidadeDestino + 1 >= RATOSALERTSTART
							&& quantidadeDestino + 1 < MAXRATOS) {
						System.out.println("Sala 1 ou + ratos");
						salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
						salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
						writeToMySQL(data, "medicoes_passagens");
						inserted = true;
						if (hasMaxTimePassed(data, lastInsertedRat)) {
							writeAlertaToMySQL(data, RATOSPARADOS, "Ratos ficaram parados durante muito tempo");
							inserirDataFimExperiencia(idExperienciaFromSQL());
						}
						updateSala(salaDestino, quantidadeDestino + 1);
						updateSala(salaOrigem, quantidadeOrigem - 1);

						if (ultimoAlertaLimiteRatos(salaDestino)) {
							System.out.println("Encontrei alerta 1 ");
							TEMPOALERTASRATOS = tempoEntreAlertasRatos();
							if (TEMPOALERTASRATOS != -1
									&& System.nanoTime() - TEMPOALERTASRATOSAUX > TEMPOALERTASRATOS * 1000000000) {
								System.out.println("Tempo esta okay 1");
								writeAlertaToMySQL(data, RatosQuaseMax,
										"Numero de ratos numa esta quase no maximo " + salaDestino);
							}
						} else {
							System.out.println("nada de alerta 2");
							System.err.println(" tempo esta okay 2");
							alertaInseridoRatos = true;
							writeAlertaToMySQL(data, RatosQuaseMax,
									"Numero de ratos numa esta quase no maximo " + salaDestino);

						}
					} else {
						salasMap.put(salaOrigem, salasMap.get(salaOrigem) - 1);
						salasMap.put(salaDestino, salasMap.get(salaDestino) + 1);
						writeToMySQL(data, "medicoes_passagens");
						inserted = true;
						if (hasMaxTimePassed(data, lastInsertedRat)) {
							writeAlertaToMySQL(data, RATOSPARADOS, "Ratos ficaram parados durante muito tempo");
							inserirDataFimExperiencia(idExperienciaFromSQL());
						}
						updateSala(salaDestino, quantidadeDestino + 1);
						updateSala(salaOrigem, quantidadeOrigem - 1);
					}
				}
			}

			if (inserted) {
				lastInsertedRat = data;
				inserted = false;
			}
		}
	}

	private boolean hasMaxTimePassed(String data, String lastInsertedRat2) {

		if (data == "" || lastInsertedRat2 == "")
			return false;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

		String horaData = extractValue(data, "Hora");
		String horaLast = extractValue(lastInsertedRat2, "Hora");

		LocalDateTime dateTimeHoraData = LocalDateTime.parse(horaData, formatter);
		LocalDateTime dateTimeHoraLast = LocalDateTime.parse(horaLast, formatter);

		long miliHoraData = dateTimeHoraData.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
		long miliHoraLast = dateTimeHoraLast.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
		MAXTIMEPARADOS = tempoParadosPorSala();
		if (MAXTIMEPARADOS == -1) {
			return false;
		}

		if (miliHoraLast + (MAXTIMEPARADOS * 1000) <= miliHoraData) {
			return true;
		}

		return false;
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

	// Datas Outliers

	private void detetarOutliers(ArrayList<String> dadosCorretos) {
		System.out.println("Vou ver outliers: " + dadosCorretos.size());
		ArrayList<String> outliers = new ArrayList<String>();
		System.err.println(dadosCorretos.size());
		for (int i = 0; i < dadosCorretos.size(); i++) {
			System.out.println(dadosCorretos.get(i));
			// Calcula o IQR
			float Q1 = calculatePercentile(1);
			float Q3 = calculatePercentile(2);

			if (Q1 != Float.NEGATIVE_INFINITY && Q3 != Float.NEGATIVE_INFINITY) {
				System.err.println("NAO ERA INFINITO");
				float IQR = Q3 - Q1;

				// Define os limites
				float lowerLimit = Q1 - 1.5f * IQR;
				float upperLimit = Q3 + 1.5f * IQR;

				float temperatura = extrairValorLeitura(dadosCorretos.get(i));
				// Verifica se a temperatura é um outlier
				if (temperatura < lowerLimit || temperatura > upperLimit) {
					outliers.add(dadosCorretos.get(i));
					dadoSet.add(dadosCorretos.get(i));
					// System.out.println("Temperatura É um outlier: " + temperatura);
				} else {
					// Se não for um outlier, insere no conjunto de dados

					dadoSet.add(dadosCorretos.get(i));
					writeToMySQL(dadosCorretos.get(i), "medicoes_temperatura");

					// CHECK ALERTAS

					// check if temperatura is within range returned in funcitons minTempForAlert
					// and maxTempForAlert
					// check if experience ends if the temperature is bigger than maxTemperature or
					// less than minTemperature

					if (temperatura <= minTemperature()) {
						writeAlertaToMySQL(dadosCorretos.get(i), TemperaturaMin,
								"Temperatura baixa extrema na sala " + extractRoom(dadosCorretos.get(i), "Sensor"));
						// acabar experiencia
						inserirDataFimExperiencia(idExperienciaFromSQL());
					} else if (temperatura >= maxTemperature()) {

						writeAlertaToMySQL(dadosCorretos.get(i), TemperaturaMax,
								"Temperatura alta extrema na sala " + extractRoom(dadosCorretos.get(i), "Sensor"));
						// acabar experiencia
						inserirDataFimExperiencia(idExperienciaFromSQL());
					} else if (minTempForAlert() != -1 && temperatura <= minTempForAlert()) {
						// check if temperatura has the same value as last temperatura alert
						// if it has the same value, check if the time between the two alerts is bigger
						// than the time between alerts
						if ((temperatura == last_temperature_alert
								&& last_timestamp_alert + (tempoEntreAlertasTemperatura() * 1000 * 60) < System
										.currentTimeMillis())
								|| temperatura != last_temperature_alert) {

							writeAlertaToMySQL(dadosCorretos.get(i), TemperaturaQuaseMin,
									"Temperatura baixa na sala " + extractRoom(dadosCorretos.get(i), "Sensor"));
						}
					} else if (temperatura >= maxTempForAlert()) {
						// check if temperatura has the same value as last temperatura alert
						// if it has the same value, check if the time between the two alerts is bigger
						// than the time between alerts
						if ((temperatura == last_temperature_alert
								&& last_timestamp_alert + (tempoEntreAlertasTemperatura() * 1000 * 60) < System
										.currentTimeMillis())
								|| temperatura != last_temperature_alert) {

							writeAlertaToMySQL(dadosCorretos.get(i), TemperaturaQuaseMax,
									"Temperatura alta na sala " + extractRoom(dadosCorretos.get(i), "Sensor"));
						}
					}

					last_temperature_alert = temperatura;
					last_timestamp_alert = System.currentTimeMillis();
				}

			} else {
				dadoSet.add(dadosCorretos.get(i));
				writeToMySQL(dadosCorretos.get(i), "medicoes_temperatura");
			}

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
		System.out.println("start = " + start);
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

	// Validações

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
					dadosCorretos.add(data);
				}
			}
		}

		// writeArrayListToFile(dadosCorretos, "DadosCorretosTemperatura.txt");
		// writeArrayListToFile(dadosAnomalos, "DadosAnomalosTemperatura.txt");
		return dadosCorretos;
	}

	private void validarFormatosSalas(ArrayList<String> dateListRatos) {

		System.out.println("Vamos validar as salas");

		ArrayList<String> dadosAnomalos = new ArrayList<String>();
		ArrayList<String> dadosCorretos = new ArrayList<String>();
		for (String data : dateListRatos) {
			boolean anomalia = false; // Variável para verificar se é uma anomalia
			if (!data.contains("Hora") || !data.contains("SalaDestino") || !data.contains("SalaOrigem")) {
				// System.out.println("Formato errado logo é anomalo " + data);
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

								// System.err.println("Data mal formatada" + valor);
								dadosAnomalos.add(data);
								// System.err.println("HORA MAL FORMATADA");
								anomalia = true; // Define como anomalia se Hora estiver mal formatada

							} else if (!isValidDate(valor)) {
								// System.err.println("Data invalida" + valor);

								dadosAnomalos.add(data);
								anomalia = true;

							} else if (chave.equals("SalaDestino")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {

									// System.err.println("Sala Destino MAL FORMATADA");
									dadosAnomalos.add(data);
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							} else if (chave.equals("SalaOrigem")) {
								try {
									Integer.parseInt(valor);
								} catch (NumberFormatException e) {
									// System.err.println("Sala Destino MAL FORMATADA");
									dadosAnomalos.add(data);
									anomalia = true; // Define como anomalia se Leitura estiver mal formatada
								}
							}
						}
					}
				}
				if (!anomalia) {
					// Se não for uma anomalia, adicione aos dados corretos
					// System.out.println("Tudo bem com este " + data);
					dadosCorretos.add(data);
				}

			}
		}

		// writeArrayListToFile(dadosCorretos, "DadosCorretosSalas.txt");
		// writeArrayListToFile(dadosAnomalos, "DadosAnomalosSalas.txt");

		moverRatos(dadosCorretos, dadosAnomalos);
	}

	// Funções auxiliares relacionadas com o MySQL

	// /queries vara ir buscar todas as variaveis necessárias dos parametros
	// adicionais
	// Ir buscar id da experiencia ativa - Sim
	// Max ratos sala - Sim
	// Num ratos minimo por sala para alerta - Sim
	// Max tempo parados - Sim
	// Numero de ratos que iniciam a experiencia - Sim
	// Temperatura minima - Sim
	// Temperatura maxima para começar alertas do minimo -Sim
	// Temperatura minima para alerta do maximo- Sim
	// Temperatura maxima -Sim
	// Intervalo de tempo entre alertas Temperatura - Sim
	// Intervalo de tempo entre alertas Ratos - Sim

	// Funcao para inserir na expeiencia que nao tiver a hora preenchida,
	// preenche-la com a data de fim da experiencia: data atual

	private void popularMedicoes() {
		int expId = idExperienciaFromSQL();
		int numOfRooms = getTotalNumberOfRooms();
		int num_ratos = 0;
		if (expId == -1 || numOfRooms == -1)
			return;

		try {
			Statement s = connTo.createStatement();
			for (int i = 1; i <= numOfRooms; i++) {
				String SqlComando = "INSERT INTO medicoes (id_ex, num_ratos, sala, hora) VALUES (" + expId + ", "
						+ num_ratos + ", '" + i + "', CURRENT_TIMESTAMP);";
				ResultSet rs = s.executeQuery(SqlComando);
			}
			s.close();
		} catch (Exception e) {
			if (e instanceof java.sql.SQLNonTransientConnectionException) {
				this.connectDatabase_to();
				System.out.println("Error Inserting in the database normal value. " + e);
				// Em caso de falha, aguarde antes de tentar novamente
				try {
					Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	private void updateSala(int sala, int newValue) {
		int id = idExperienciaFromSQL();
		if (id == -1)
			return;

		String SqlCommando = "UPDATE medicoes SET num_ratos = " + newValue + ", hora = CURRENT_TIMESTAMP WHERE sala = '"
				+ sala + "';";
		boolean commandExecutedSuccessfully = false;

		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				s.executeQuery(SqlCommando);
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					commandExecutedSuccessfully = true;
				}
				System.out.println("Error updating the database. " + e);
			}
		}
	}

	private boolean ultimoAlertaLimiteRatos(int salaDestino) {
		String SqlCommando = "SELECT * FROM alerta WHERE tipo_alerta = 'QuaseMaxRatos' AND sala = " + salaDestino
				+ " ORDER BY hora_real DESC LIMIT 1;";
		boolean result = false;
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				result = rs.next();
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					System.out.println("Error Inserting in the database normal value. " + e);
					// Em caso de falha, aguarde antes de tentar novamente
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					result = false;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return result;
	}

	public void inserirDataFimExperiencia(int id) {
		if (id == -1)
			return;
		String SqlCommando = "UPDATE experiencia SET data_hora_fim = CURRENT_TIMESTAMP WHERE id_ex= " + id + ";";
		boolean commandExecutedSuccessfully = false;

		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				s.executeQuery(SqlCommando);
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					commandExecutedSuccessfully = true;
				}

				System.out.println("Error updating the database. " + e);
			}
		}
	}

	public int tempoEntreAlertasTemperatura() {
		int num = -1;
		String SqlCommando = "SELECT Tempo_Alerta_TEMPERATURA FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1;
					commandExecutedSuccessfully = true;
				}

				System.out.println("Error Inserting in the database normal value. " + e);
				// Em caso de falha, aguarde antes de tentar novamente
			}
		}
		return num;

	}

	public int tempoEntreAlertasRatos() {
		int num = -1;
		String SqlCommando = "SELECT Tempo_Alerta_RATOS FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";

		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return num;
	}

	// Limite inferior dos alertas de temperatura alta (temp max = 20, este valor é
	// por exemplo 18)
	public double maxTempForAlert() {
		double num = -1.00;
		String SqlCommando = "SELECT MAX_TEMPERATURA_FOR_ALERT FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getDouble(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1.00;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return num;
	}

	public double maxTemperature() {
		System.err.println("Max Temperature Inicio");
		double num = -1.00;
		String SqlCommando = "SELECT maxTemperatura FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {

			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getDouble(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1.00;
					commandExecutedSuccessfully = true;
				}
			}
		}
		System.err.println("Max Temperature Fim : " + num);

		return num;

	}

	// Limite superior dos alertas de temperatura baixa (temp min = 10, este valor é
	// por exemplo 12)
	public double minTempForAlert() {
		double num = -1.00;
		String SqlCommando = "SELECT MIN_TEMPERATURA_FOR_ALERT FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				System.err.println("query coias 1");
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getDouble(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1.00;
					commandExecutedSuccessfully = true;
				}
			}
		}
		System.err.println("query coias 2");
		return num;
	}

	public double minTemperature() {
		double num = -1.00;
		String SqlCommando = "SELECT minTemperatura FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getDouble(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1.00;
					commandExecutedSuccessfully = true;
				}
			}
		}
		System.out.println("minTemp :" + num);
		return num;
	}

	public int numRatosMinimoParaAlerta() {
		int num = -1;
		String SqlCommando = "SELECT NUMBER_RATOS_FOR_ALERT FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT 1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					num = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					num = -1;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return num;
	}

	public int tempoParadosPorSala() {
		int tempo = -1;
		String SqlCommando = "SELECT TempoMaximoPermanenciaSala AS maximo_de_ratos_por_sala FROM parametro_adicionais ORDER BY id_parametros DESC LIMIT   1;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					tempo = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					tempo = -1;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return tempo;
	}

	public int numMaxRatosSala() {
		int numMaxRatos = -1;
		String SqlCommando = "SELECT RatosNumaSala AS maximo_de_ratos_por_sala FROM parametro_adicionais ORDER BY id_parametros  DESC LIMIT  1;";

		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					numMaxRatos = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					numMaxRatos = -1;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return numMaxRatos;
	}

	public int inicialNumRatos() {
		int numRatos = -1;
		String SqlCommando = "SELECT num_ratos FROM experiencia WHERE id_ex = " + idExperienciaFromSQL() + ";";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlCommando);
				if (rs.next()) {
					numRatos = rs.getInt(1);
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					numRatos = -1;
					commandExecutedSuccessfully = true;
				}
			}
		}
		return numRatos;
	}

	public int idExperienciaFromSQL() {
		int maxIdExperiencia = -1;
		// Só isto não funciona, a experiência tem de ter data final a NULL para saber
		// que não acabou
		// String SqlComando = "SELECT MAX(id_ex) FROM experiencia;";
		// Só vai buscar a mais recetne se tiver parametros adicionais, até lá não conta
		// como experiência ativax
		String SqlComando = "SELECT e.* FROM experiencia e JOIN parametro_adicionais p ON e.id_ex = p.id_ex WHERE e.id_ex = ( SELECT MAX(id_ex) FROM experiencia ) AND e.data_hora_fim IS NULL;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connTo.createStatement();
				ResultSet rs = s.executeQuery(SqlComando);
				if (rs.next()) {
					maxIdExperiencia = rs.getInt(1); // Obtém o valor máximo da coluna id_experiencia
					// Use o valor maxIdExperiencia conforme necessário
					// System.out.println("Maior valor de id_experiencia: " + maxIdExperiencia);
				} else {
					maxIdExperiencia = -1;
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					maxIdExperiencia = -1;
					commandExecutedSuccessfully = true;

				}
				System.out.println("Erro ao buscar o maior valor de id_experiencia: " + e);
			}
		}
		return maxIdExperiencia;
	}

	public int getTotalNumberOfRooms() {
		int totalNumberOfRooms = -1;
		String SqlComando = "SELECT numerodesalas from configuracaolabirinto;";
		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				Statement s = connToStor.createStatement();
				ResultSet rs = s.executeQuery(SqlComando);
				if (rs.next()) {
					totalNumberOfRooms = rs.getInt(1); // Obtém o valor máximo da coluna id_experiencia
					// Use o valor maxIdExperiencia conforme necessário
					// System.out.println("Maior valor de id_experiencia: " + maxIdExperiencia);
				} else {
					totalNumberOfRooms = -1;
				}
				s.close();
				commandExecutedSuccessfully = true;
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					this.connectDatabase_to();
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				} else {
					totalNumberOfRooms = -1;
					commandExecutedSuccessfully = true;
				}
				System.out.println("Erro ao buscar o maior valor de id_experiencia: " + e);
			}
		}
		return totalNumberOfRooms;
	}

	public void writeToMySQL(String c, String tabela) {
		String SqlCommando = "";
		String hora = "";
		String leitura = "";
		String sensor = "";
		String salaOrigem = "";
		String salaDestino = "";
		int mongoID = -1;
		int id = -1;

		switch (tabela) {
			case "medicoes_temperatura":
				hora = extractValue(c, "Hora");
				if (hora.length() == 19) {
					hora += ".075649";
				}
				leitura = extractValue(c, "Leitura");
				if (leitura != "NULL") {
					leitura = dfZero.format(Double.parseDouble(leitura));
					if (Double.parseDouble(leitura) >= 10000.0) {
						leitura = "9999.99";
					}
				}

				sensor = extractValue(c, "Sensor");
				mongoID = Integer.parseInt(extractValue(c, "id"));
				id = idExperienciaFromSQL();
				if (id == -1) {
					SqlCommando = "Insert into medicoes_temperatura" + " (" + "id_ex, hora, leitura, sensor"
							+ ") values ("
							+ "NULL"
							+ ",'" + hora + "', " + leitura + ", " + sensor + ");";
				} else {
					SqlCommando = "Insert into medicoes_temperatura" + " (" + "id_ex, hora, leitura, sensor"
							+ ") values ("
							+ id
							+ ",'" + hora + "', " + leitura + ", " + sensor + ");";
				}

				break;

			case "medicoes_passagens":
				hora = extractValue(c, "Hora");
				if (hora.length() == 19) {
					hora += ".075649";
				}
				mongoID = Integer.parseInt(extractValue(c, "id"));

				salaOrigem = extractValue(c, "SalaOrigem");
				salaDestino = extractValue(c, "SalaDestino");
				id = idExperienciaFromSQL();
				if (id == -1)
					return;

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
				if (tabela.equals("medicoes_passagens")) {
					writeInMongoBackupValue("sensoresPortas", mongoID);
				} else {
					writeInMongoBackupValue("sensoresTemp" + sensor, mongoID);
				}
				s.close();
				commandExecutedSuccessfully = true; // Define como verdadeiro se a execução foi bem-sucedida
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
				}

				System.out.println("Error Inserting in the database normal value. " + e);
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
		String SqlCommando = null;
		if (c == null) {
			int id = idExperienciaFromSQL();
			if (id == -1)
				return;

			SqlCommando = "Insert into alerta" + " ("
					+ "id_ex, hora_real, tipo_alerta, mensagem, hora_chegada"
					+ ") values (" + id + ", CURRENT_TIMESTAMP" + ", '" + tipo_alerta + "', '"
					+ mensagem + "', CURRENT_TIMESTAMP );";
		} else {
			String hora = extractValue(c, "Hora") == null ? "NULL" : extractValue(c, "Hora");
			if (!hora.equals("NULL") && hora.length() == 19) {
				hora += ".075649";
			}
			String salaOrigem = extractValue(c, "SalaOrigem") == null ? "NULL" : extractValue(c, "SalaOrigem");
			String salaDestino = extractValue(c, "SalaDestino") == null ? "NULL" : extractValue(c, "SalaDestino");
			int id = idExperienciaFromSQL();
			String leitura = extractValue(c, "Leitura") == null ? "NULL" : extractValue(c, "Leitura");
			if (leitura != "NULL") {
				leitura = dfZero.format(Double.parseDouble(leitura));
				if (Double.parseDouble(leitura) >= 10000.0) {
					leitura = "9999.99";
				}
			}
			String sensor = extractValue(c, "Sensor") == null ? "NULL" : extractValue(c, "Sensor");
			SqlCommando = "";
			if (id == -1)
				return;

			SqlCommando = "Insert into alerta" + " ("
					+ "id_ex, hora_real, sala, sensor, leitura, tipo_alerta, mensagem , hora_chegada"
					+ ") values (" +
					id + " , ' " + hora + "' , " + salaDestino + " , " + sensor + " , " + leitura + " , '"
					+ tipo_alerta
					+ "' , '" + mensagem + "' , CURRENT_TIMESTAMP );";

		}

		boolean commandExecutedSuccessfully = false;
		while (!commandExecutedSuccessfully) {
			try {
				documentLabel.append(SqlCommando.toString() + "\n");
				Statement s = connTo.createStatement();
				int result = s.executeUpdate(SqlCommando);

				s.close();
				commandExecutedSuccessfully = true; // Define como verdadeiro se a execução foi bem-sucedida
			} catch (Exception e) {
				if (e instanceof java.sql.SQLNonTransientConnectionException) {
					new WriteMysql().connectDatabase_to();
				}

				System.out.println("Error Inserting in the database alerta. " + e);
				// Em caso de falha, aguarde antes de tentar novamente
				try {
					Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public void writeMySQLFinalRoomResult(HashMap<Integer, Integer> roomFinalMap) {
		int id = idExperienciaFromSQL();
		for (int room : roomFinalMap.keySet()) {
			String SqlComando = "Insert into medicoes" + " (" + "id_ex, num_ratos, sala, hora" + ") values (" + id
					+ ", " + roomFinalMap.get(room) + ", " + room + ", CURRENT_TIMESTAMP);";
			boolean commandExecutedSuccessfully = false;
			while (!commandExecutedSuccessfully) {
				try {
					Statement s = connTo.createStatement();
					ResultSet rs = s.executeQuery(SqlComando);
					s.close();
					commandExecutedSuccessfully = true; // Define como verdadeiro se a execução foi bem-sucedida
				} catch (Exception e) {
					System.out.println("Resultado para a sala " + room + " não inserido");
					if (e instanceof java.sql.SQLNonTransientConnectionException) {
						new WriteMysql().connectDatabase_to();
					}

					System.out.println("Error Inserting in the database alerta. " + e);
					// Em caso de falha, aguarde antes de tentar novamente
					try {
						Thread.sleep(1000); // Aguarda por 1 segundo antes de tentar novamente
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

	}

	// Funções auxiliares relacionadas com o mongo
	private void writeInMongoBackupValue(String colIdUpdate, int newValue) {
		Document filter = new Document("name", colIdUpdate);
		Document update = new Document("$set", new Document("id", newValue));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
				.returnDocument(com.mongodb.client.model.ReturnDocument.AFTER);
		Document sequenceDocument = db2.getCollection("lastInsertedIds").findOneAndUpdate(filter, update, options);
		System.out.println(sequenceDocument + "\n");

		if (sequenceDocument == null) {
			db2.getCollection("lastInsertedIds")
					.insertOne(new Document("name", colIdUpdate).append("id", newValue));
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

		// for (String a : ids.keySet()) {
		// System.err.println(a + " " + ids.get(a));
		// }

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

	// Funções para escrever os dados anomalos nos ficheiros

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

	// Função que lê o ficheiro e atribui os dados às estáticas
	public void readFile() {
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

			sql_maze_database_connection_to = p.getProperty("sql_maze_database_connection_to");
			sql_maze_database_user_to = p.getProperty("sql_maze_database_user_to");
			sql_maze_database_password_to = p.getProperty("sql_maze_database_password_to");

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
	}

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

	// Ligar à cloud para ir buscar os dados do labirinto
	public void connectMazeMySQL() {
		System.out.println("GETTING MAZE INFO");
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			connToStor = DriverManager.getConnection(sql_maze_database_connection_to, sql_maze_database_user_to,
					sql_maze_database_password_to);
			documentLabel.append("SQl Connection:" + sql_maze_database_connection_to + "\n");
			documentLabel
					.append("Connection To MariaDB Destination " + sql_maze_database_connection_to + " Suceeded"
							+ "\n");

		} catch (Exception e) {
			System.out.println("Mysql Server Destination down, unable to make the connection. " + e);
		}
	}

	// Ligar ao nosso Mysql para que possam ser feitas as ações necessárias
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

	// MAIN
	public static void main(String[] args) {
		WriteMysql programa = new WriteMysql();
		createWindow();
		programa.readFile();
		programa.connectToMongo();
		programa.connectDatabase_to();
		programa.connectMazeMySQL();
		programa.ReadData();

	}
}