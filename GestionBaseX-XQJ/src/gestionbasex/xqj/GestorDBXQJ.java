package gestionbasex.xqj;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQExpression;
import javax.xml.xquery.XQResultSequence;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GestorDBXQJ {

    private final String dataBase = "empresa";
    private final String user = "admin";
    private final String password = "admin";

    private XQConnection connection;

    public GestorDBXQJ() {

        conectar();
    }

    public void conectar() {

        try {
            XQDataSource dataSource = new org.basex.api.xqj.BXQDataSource();

            dataSource.setProperty("user", user);
            dataSource.setProperty("password", password);

            this.connection = dataSource.getConnection();

            System.out.println("Conexión con base de datos '" + this.dataBase + "' creada con éxito");
        } catch (Exception e) {
            System.out.println("No se ha podido establecer la conexión:");
            System.out.println("---> " + e);
        }
    }

    public void desconectar() {

        try {
            if (this.connection == null) {
                System.out.println("La conexión ya está cerrada");
            } else {
                this.connection.close();
                System.out.println("Conexión cerrada con éxito");
            }
        } catch (Exception e) {
            System.out.println("No se ha podido cerrar la conexión:");
            System.out.println("---> " + e);
        }
    }

    public Departamento getDepartamentoSolo(String codigo) {

        try {
            XQExpression consulta = this.connection.createExpression();
            String lineaConsulta = "collection('empresa')/empresa/departaments/dept[@codi=\"" + codigo + "\"]";
            XQResultSequence resultado = consulta.executeQuery(lineaConsulta);
            if (!resultado.next()) {
                throw new Exception();
            }
            Node nodoPadre = resultado.getNode();
            NodeList listaNodos = nodoPadre.getChildNodes();
            int listaSize = listaNodos.getLength();

            String nombre = null;
            String localidad = null;

            for (int i = 0; i < listaSize; i++) {
                Node nodoHijo = listaNodos.item(i);
                if (nodoHijo.getNodeType() == Node.ELEMENT_NODE) {

                    if (nodoHijo.getNodeName().equals("nom")) {
                        nombre = nodoHijo.getChildNodes().item(0).getNodeValue();
                    }
                    if (nodoHijo.getNodeName().equals("localitat")) {
                        localidad = nodoHijo.getChildNodes().item(0).getNodeValue();
                    }
                }
            }
            if (nombre == null) {
                throw new Exception();
            }

            Departamento departamento = new Departamento(codigo, nombre, localidad);
            return departamento;
        } catch (Exception e) {
            System.out.println("No se ha podido recuperar el departamento:");
            System.out.println("---> " + e);
            return null;
        }
    }

    public Departamento getDepartamentoCompleto(String codigoDep) {

        try {
            Departamento departamento = getDepartamentoSolo(codigoDep);

            ArrayList<Empleado> listaEmpleados = new ArrayList();

            XQExpression consulta = this.connection.createExpression();
            String lineaConsulta = "collection('empresa')/empresa/empleats/emp[@dept=\"" + codigoDep + "\"]";
            XQResultSequence resultado = consulta.executeQuery(lineaConsulta);

            while (resultado.next()) {

                Empleado empleado = generarEmpleado(resultado.getNode(), codigoDep);

                listaEmpleados.add(empleado);
            }
            departamento.setEmpleados(listaEmpleados);
            return departamento;
        } catch (Exception e) {
            System.out.println("No se ha podido recuperar el departamento completo:");
            System.out.println("---> " + e);
            return null;
        }
    }

    public void insertDepartamento(Departamento departamento) {

        try {
            if (departamentoYaExistente(departamento)) {
                throw new Exception();
            }

            String lineaInsercion = departamento.getCodigoInsercion();

            if (departamento.getEmpleados() != null) {
                ArrayList<Empleado> listaEmpleados = departamento.getEmpleados();
                for (Empleado empleado : listaEmpleados) {
                    lineaInsercion += ", " + empleado.getCodigoInsercion();
                }
            }

            XQExpression insercion = this.connection.createExpression();
            insercion.executeQuery(lineaInsercion);

            System.out.println("Departamento insertado con éxito");
        } catch (Exception e) {
            System.out.println("Error al insertar el departamento:");
            System.out.println("---> " + e);
        }
    }

    public void deleteDepartamento(Departamento departamento, Departamento departamentoNuevo) {

        try {
            if (!departamentoYaExistente(departamento)) {
                throw new Exception();
            }
            String codigo = departamento.getCodigo();

            String linea = "let $dept:= collection('empresa')/empresa/departaments/dept[@codi=\"" + codigo + "\"] "
                    + "return ("
                    + "delete node $dept, ";

            if (departamentoNuevo != null) {
                if (!departamentoYaExistente(departamentoNuevo)) {
                    throw new Exception();
                }
                String codigoNuevo = departamentoNuevo.getCodigo();
                linea += "for $emp in collection('empresa')/empresa/empleats/emp[@dept=\"" + codigo + "\"]/@dept "
                        + "return replace value of node $emp with \"" + codigoNuevo + "\")";
            } else {
                linea += "for $emp in collection('empresa')/empresa/empleats/emp[@dept=\"" + codigo + "\"] "
                        + "return delete node $emp)";
            }

            XQExpression insercion = this.connection.createExpression();
            insercion.executeQuery(linea);
            
            System.out.println("Departamento eliminado con éxito");
        } catch (Exception e) {
            System.out.println("Error al eliminar el departamento:");
            System.out.println("---> " + e);
        }
    }

    public void replaceDepartamento(Departamento dExistente, Departamento dNuevo) {

        insertDepartamento(dNuevo);

        deleteDepartamento(dExistente, dNuevo);
    }

    private Empleado generarEmpleado(Node nodoPadre, String codigoDep) {

        String codigo = null;
        String apellido = null;
        String oficio = null;
        Date fechaAlta = null;
        int salario = 0;
        int comision = 0;
        String codigoJefe = null;

        NamedNodeMap atributosPadre = nodoPadre.getAttributes();

        codigo = atributosPadre.getNamedItem("codi").getNodeValue();
        if (atributosPadre.getNamedItem("cap") != null) {
            codigoJefe = atributosPadre.getNamedItem("cap").getNodeValue();
        }
        NodeList listaNodos = nodoPadre.getChildNodes();
        int listaSize = listaNodos.getLength();
        for (int i = 0; i < listaSize; i++) {
            Node nodoHijo = listaNodos.item(i);
            if (nodoHijo.getNodeType() == Node.ELEMENT_NODE) {

                if (nodoHijo.getNodeName().equals("cognom")) {
                    apellido = nodoHijo.getChildNodes().item(0).getNodeValue();
                }
                if (nodoHijo.getNodeName().equals("ofici")) {
                    oficio = nodoHijo.getChildNodes().item(0).getNodeValue();
                }
                if (nodoHijo.getNodeName().equals("dataAlta")) {
                    fechaAlta = stringToDate(nodoHijo.getChildNodes().item(0).getNodeValue());
                }
                if (nodoHijo.getNodeName().equals("salari")) {
                    salario = stringToInt(nodoHijo.getChildNodes().item(0).getNodeValue());
                }
                if (nodoHijo.getNodeName().equals("comissio")) {
                    comision = stringToInt(nodoHijo.getChildNodes().item(0).getNodeValue());
                }
            }
        }
        Empleado empleado = new Empleado(codigo, apellido, oficio, fechaAlta, salario, comision, codigoJefe, codigoDep);
        return empleado;
    }

    private Date stringToDate(String fechaSinProcesar) {

        try {
            SimpleDateFormat formatoFecha = new SimpleDateFormat("yyyy-mm-dd");
            Date fechaProcesada = formatoFecha.parse(fechaSinProcesar);
            return fechaProcesada;
        } catch (ParseException e) {
            System.out.println("Error al convertir la fecha:");
            System.out.println("---> " + e);
            return null;
        }
    }

    private int stringToInt(String numeroSinProcesar) {

        int numeroProcesado = 0;
        if (!numeroSinProcesar.equals("")) {
            numeroProcesado = Integer.parseInt(numeroSinProcesar);
        }
        return numeroProcesado;
    }

    private boolean departamentoYaExistente(Departamento departamentoCandidato) {

        try {
            String codigoCandidato = departamentoCandidato.getCodigo();
            XQExpression consulta = this.connection.createExpression();
            String lineaConsulta = "collection('empresa')/empresa/departaments/dept[@codi=\"" + codigoCandidato + "\"]";
            XQResultSequence resultado = consulta.executeQuery(lineaConsulta);
            if (resultado.next()) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
