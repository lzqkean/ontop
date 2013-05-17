package it.unibz.krdb.obda.owlapi3.directmapping;

import it.unibz.krdb.obda.exception.DuplicateMappingException;
import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.Function;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDADataSource;
import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.Predicate;
import it.unibz.krdb.obda.model.Predicate.COL_TYPE;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.model.impl.OBDAModelImpl;
import it.unibz.krdb.sql.DataDefinition;
import it.unibz.krdb.sql.JDBCConnectionManager;
import it.unibz.krdb.sql.TableDefinition;

import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;


/***
 * 
 * A class that provides manipulation for Direct Mapping
 * 
 * @author Victor
 *
 */


public class DirectMappingEngine {
	
	private JDBCConnectionManager conMan;
	private String baseuri;
	
	public DirectMappingEngine(){
		conMan = JDBCConnectionManager.getJDBCConnectionManager();
		baseuri = new String("http://example.org/");
	}
	
	
	
	/*
	 * set the base URI used in the ontology
	 */
	public void setBaseURI(String prefix){
		if(prefix.endsWith("#")){
			this.baseuri = prefix.replace("#", "/");
		}else if(prefix.endsWith("/")){
			this.baseuri = prefix;
		}else this.baseuri = prefix+"/";
	}
	
	
	
	/***
	 * enrich the ontology according to the datasources specified in the OBDAModel
	 * basically from the database structure
	 * 
	 * @param ontology
	 * @param model
	 * 
	 * @return null
	 * 		   the ontology is updated
	 * 
	 * @throws Exceptions
	 */
			
	public void enrichOntology(OWLOntology ontology, OBDAModel model) throws OWLOntologyStorageException, SQLException{
		List<OBDADataSource> sourcelist = new ArrayList<OBDADataSource>();
		sourcelist = model.getSources();
		OntoExpansion oe = new OntoExpansion();
		if(model.getPrefixManager().getDefaultPrefix().endsWith("/")){
			oe.setURI(model.getPrefixManager().getDefaultPrefix());
		}else{
			oe.setURI(model.getPrefixManager().getDefaultPrefix()+"/");
		}
		
		//For each data source, enrich into the ontology
		for(int i=0;i<sourcelist.size();i++){
			oe.enrichOntology(conMan.getMetaData(sourcelist.get(i)), ontology);
		}
	}
	
	
	
	/***
	 * enrich the ontology according to mappings used in the model
	 * 
	 * @param manager
	 * @param model
	 * 
	 * @return a new ontology storing all classes and properties used in the mappings
	 * 
	 * @throws Exceptions
	 */
	public OWLOntology getOntology(OWLOntologyManager manager, OBDAModel model) throws OWLOntologyCreationException, OWLOntologyStorageException, SQLException{
		OWLOntology ontology = manager.createOntology();
		OWLDataFactory dataFactory = manager.getOWLDataFactory();
		
		Set<Predicate> classset = model.getDeclaredClasses();
		Set<Predicate> objectset = model.getDeclaredObjectProperties();
		Set<Predicate> dataset = model.getDeclaredDataProperties();
		
		//Add all the classes
		for(Iterator<Predicate> it = classset.iterator(); it.hasNext();){
			OWLClass newclass = dataFactory.getOWLClass(IRI.create(it.next().getName().toString()));
			OWLDeclarationAxiom declarationAxiom = dataFactory.getOWLDeclarationAxiom(newclass);
			manager.addAxiom(ontology,declarationAxiom );
			manager.saveOntology(ontology);
		}
		
		//Add all the object properties
		for(Iterator<Predicate> it = objectset.iterator(); it.hasNext();){
			OWLObjectProperty newclass = dataFactory.getOWLObjectProperty(IRI.create(it.next().getName().toString()));
			OWLDeclarationAxiom declarationAxiom = dataFactory.getOWLDeclarationAxiom(newclass);
			manager.addAxiom(ontology,declarationAxiom );
			manager.saveOntology(ontology);
		}
		
		//Add all the data properties
		for(Iterator<Predicate> it = dataset.iterator(); it.hasNext();){
			OWLDataProperty newclass = dataFactory.getOWLDataProperty(IRI.create(it.next().getName().toString()));
			OWLDeclarationAxiom declarationAxiom = dataFactory.getOWLDeclarationAxiom(newclass);
			manager.addAxiom(ontology,declarationAxiom );
			manager.saveOntology(ontology);
		}
				
		return ontology;		
	}
	
	
	
	/***
	 * extract all the mappings from a datasource
	 * 
	 * @param source
	 * 
	 * @return a new OBDA Model containing all the extracted mappings
	 */
	public OBDAModel extractMappings(OBDADataSource source) throws SQLException, DuplicateMappingException{
		OBDAModelImpl model = new OBDAModelImpl();
		insertMapping(source, model);
		return model;
	}
	
	
	
	/***
	 * extract mappings from given datasource, and insert them into the given model
	 * 
	 * @param source
	 * @param model
	 * 
	 * @return null
	 * 
	 * Duplicate Exception may happen,
	 * since mapping id is generated randomly and same id may occur
	 */
	public void insertMapping(OBDADataSource source, OBDAModel model) throws SQLException, DuplicateMappingException{		
		if (baseuri == null || baseuri.isEmpty())
			this.baseuri =  model.getPrefixManager().getDefaultPrefix();
		model.addSource(source);
		for(int i=0;i<conMan.getMetaData(source).getTableList().size();i++){
			TableDefinition td = conMan.getMetaData(source).getTableList().get(i);
			model.addMappings(source.getSourceID(), getMapping(td, source));
		}	
		System.out.println(model.getMappings().toString());
		
		for (URI uri : model.getMappings().keySet())
			for (OBDAMappingAxiom axiom : model.getMappings().get(uri))
			{
				CQIE query = (CQIE) axiom.getTargetQuery();
				for (Function f : query.getBody()) {
					if (f.getArity() == 1)
						model.declareClass(f.getFunctionSymbol());
					else if (f.getFunctionSymbol().getType(1).equals(COL_TYPE.OBJECT))
						model.declareObjectProperty(f.getFunctionSymbol());
					else 
						model.declareDataProperty(f.getFunctionSymbol());
				}
			}
	}
	
	
	
	/***
	 * generate a mapping axiom from a table of some database
	 * 
	 * @param table : the datadefinition from which mappings are extraced
	 * @param source : datasource that the table may refer to, such as foreign keys
	 * 
	 *  @return a new OBDAMappingAxiom 
	 */
	public List<OBDAMappingAxiom> getMapping(DataDefinition table, OBDADataSource source) throws SQLException{
		DirectMappingAxiom dma = new DirectMappingAxiom(baseuri, table, conMan.getMetaData(source));
		dma.setbaseuri(this.baseuri);
		OBDADataFactory dfac = OBDADataFactoryImpl.getInstance();
		
		List<OBDAMappingAxiom> axioms = new ArrayList<OBDAMappingAxiom>();
		axioms.add(dfac.getRDBMSMappingAxiom(dma.getSQL(), dma.getCQ(dfac)));
		
		List<String> refSqls = dma.getRefSQLs();
		List<CQIE> refCQs = dma.getRefCQs(dfac);
		for (int i=0; i< refSqls.size(); i++)
			axioms.add(dfac.getRDBMSMappingAxiom(refSqls.get(i), refCQs.get(i)));
		
		return axioms;
	}
	

	
	
	
	
	

}
