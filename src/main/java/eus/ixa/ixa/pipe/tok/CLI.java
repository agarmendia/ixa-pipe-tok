/*
 *Copyright 2015 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package eus.ixa.ixa.pipe.tok;

import ixa.kaflib.KAFDocument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.Properties;
import java.io.File;
import java.io.FileWriter;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import org.jdom2.JDOMException;

/**
 * ixa-pipe-tok provides several configuration parameters:
 * 
 * <ol>
 * <li>lang: choose language to create the lang attribute in KAF header.
 * <li>normalize: choose normalization method.
 * <li>outputFormat: choose between oneline, conll or NAF as output.
 * <li>untokenizable: print untokenizable (\uFFFD) characters.
 * <li>notok: take already tokenized text as input and create a KAFDocument with
 * it.
 * <li>inputkaf: take a NAF Document as input instead of plain text file.
 * <li>kafversion: specify the NAF version as parameter.
 * <li>hardParagraph: never break paragraphs.
 * <li>eval: input reference corpus to evaluate a tokenizer.
 * </ol>
 * 
 * 
 * @author ragerri
 * @version 2015-04-08
 */

public class CLI {

  /**
   * Get dynamically the version of ixa-pipe-tok by looking at the MANIFEST
   * file.
   */
  private final String version = CLI.class.getPackage()
      .getImplementationVersion();
  /**
   * Get the commit of ixa-pipe-tok by looking at the MANIFEST file.
   */
  private final String commit = CLI.class.getPackage()
      .getSpecificationVersion();
  Namespace parsedArguments = null;

  // create Argument Parser
  ArgumentParser argParser = ArgumentParsers.newArgumentParser(
      "ixa-pipe-tok-" + version + ".jar").description(
      "ixa-pipe-tok-" + version
          + " is a multilingual tokenizer developed by the IXA NLP Group.\n");
  /**
   * Sub parser instance.
   */
  private final Subparsers subParsers = argParser.addSubparsers().help(
      "sub-command help");
  /**
   * The parser that manages the tagging sub-command.
   */
  private final Subparser annotateParser;

  public CLI() {
    annotateParser = subParsers.addParser("tok").help("Tagging CLI");
    loadAnnotateParameters();
  }

  public static void main(final String[] args) throws IOException,
      JDOMException {
    final CLI cmdLine = new CLI();
    cmdLine.parseCLI(args);
  }

  /**
   * Parse the command interface parameters with the argParser.
   * 
   * @param args
   *          the arguments passed through the CLI
   * @throws IOException
   *           exception if problems with the incoming data
   * @throws JDOMException
   *           a xml exception
   */
  public final void parseCLI(final String[] args) throws IOException,
      JDOMException {
    try {
      parsedArguments = argParser.parseArgs(args);
      System.err.println("CLI options: " + parsedArguments);
      if (args[0].equals("tok")) {
        annotate(System.in, System.out);
      }
    } catch (final ArgumentParserException e) {
      argParser.handleError(e);
      System.out.println("Run java -jar target/ixa-pipe-tok-" + version
          + ".jar tok -help for details");
      System.exit(1);
    }
  }

  public final void annotate(final InputStream inputStream,
      final OutputStream outputStream) throws IOException, JDOMException {
    final String outputFormat = parsedArguments.getString("outputFormat");
    final String normalize = parsedArguments.getString("normalize");
    final String lang = parsedArguments.getString("lang");
    final String untokenizable = parsedArguments.getString("untokenizable");
    final String kafVersion = parsedArguments.getString("kafversion");
    final Boolean inputKafRaw = parsedArguments.getBoolean("inputkaf");
    final Boolean noTok = parsedArguments.getBoolean("notok");
    final String hardParagraph = parsedArguments.getString("hardParagraph");
    final Boolean serverMode = parsedArguments.getBoolean("server");
    final Properties properties = setAnnotateProperties(lang, normalize, untokenizable, hardParagraph);

    IXAdaemon.sendINIT();
    BufferedReader breader = null;
    final BufferedWriter bwriter = new BufferedWriter(new OutputStreamWriter(
        System.out, "UTF-8"));
     
   
	  IXAdaemon.sendRUN();
    KAFDocument kaf = null;

    if (serverMode) {
      annotate_server(outputFormat, properties, kafVersion, lang, kaf, noTok, inputKafRaw, breader, bwriter);
    }else{
      annotate_noserver(outputFormat, properties, kafVersion, lang, kaf, noTok, inputKafRaw, breader, bwriter);
    }
  }

  private void annotate_server(String outputFormat, Properties properties, String kafVersion, String lang, KAFDocument kaf, Boolean noTok,
                                     Boolean inputKafRaw, BufferedReader breader, BufferedWriter bwriter) throws IOException, JDOMException {
    while (true){
    
      if (noTok) {
        final BufferedReader noTokReader = new BufferedReader(
            new InputStreamReader(System.in, "UTF-8"));
        kaf = new KAFDocument(lang, kafVersion);
        final KAFDocument.LinguisticProcessor newLp = kaf.addLinguisticProcessor(
            "text", "ixa-pipe-tok-notok-" + lang, version + "-" + commit);
        newLp.setBeginTimestamp();
        Annotate.tokensToKAF(noTokReader, kaf);
        newLp.setEndTimestamp();
        bwriter.write(kaf.toString());
        noTokReader.close();
      } else {
        if (inputKafRaw) {
          kaf = null;
          final BufferedReader kafReader = new BufferedReader(
            new InputStreamReader(System.in, "UTF-8"));
          kaf = IXAdaemon.inputKafDocument(kafReader);
          System.err.println("lortu da kaf dokumentua");
          if (kaf == null) {
            System.err.println("STDIN closed. Exiting.");
            System.exit(1);
          }
          final String text = kaf.getRawText();
          final StringReader stringReader = new StringReader(text);
          breader = new BufferedReader(stringReader);
        } else {
          kaf = new KAFDocument(lang, kafVersion);
          breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }
        final Annotate annotator = new Annotate(breader, properties);
        if (outputFormat.equalsIgnoreCase("conll")) {
          if (parsedArguments.getBoolean("offsets")) {
            bwriter.write(annotator.tokenizeToCoNLL());
          } else {
            bwriter.write(annotator.tokenizeToCoNLLOffsets());
          }
        } else if (outputFormat.equalsIgnoreCase("oneline")) {
          bwriter.write(annotator.tokenizeToText());
        } else {
          final KAFDocument.LinguisticProcessor newLp = kaf
              .addLinguisticProcessor("text", "ixa-pipe-tok-" + lang, version
                  + "-" + commit);
          newLp.setBeginTimestamp();
          annotator.tokenizeToKAF(kaf);
          newLp.setEndTimestamp();
          bwriter.write(kaf.toString());
          bwriter.write("[IXAdaemon]EOD\n");
          bwriter.flush();
        }
        breader.close();
      }
    }
  }
    

  private void annotate_noserver (String outputFormat, Properties properties, String kafVersion, String lang, KAFDocument kaf, Boolean noTok,
                                     Boolean inputKafRaw, BufferedReader breader, BufferedWriter bwriter) throws IOException, JDOMException {

    if (noTok) {
      final BufferedReader noTokReader = new BufferedReader(
          new InputStreamReader(System.in, "UTF-8"));
      kaf = new KAFDocument(lang, kafVersion);
      final KAFDocument.LinguisticProcessor newLp = kaf.addLinguisticProcessor(
          "text", "ixa-pipe-tok-notok-" + lang, version + "-" + commit);
      newLp.setBeginTimestamp();
      Annotate.tokensToKAF(noTokReader, kaf);
      newLp.setEndTimestamp();
      bwriter.write(kaf.toString());
      noTokReader.close();
    } else {
      if (inputKafRaw) {
        final BufferedReader kafReader = new BufferedReader(
            new InputStreamReader(System.in, "UTF-8"));
        // read KAF from standard input
        kaf = KAFDocument.createFromStream(kafReader);
        final String text = kaf.getRawText();
        final StringReader stringReader = new StringReader(text);
        breader = new BufferedReader(stringReader);
      } else {
        kaf = new KAFDocument(lang, kafVersion);
        breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
      }
      final Annotate annotator = new Annotate(breader, properties);
      if (outputFormat.equalsIgnoreCase("conll")) {
        if (parsedArguments.getBoolean("offsets")) {
          bwriter.write(annotator.tokenizeToCoNLL());
        } else {
          bwriter.write(annotator.tokenizeToCoNLLOffsets());
        }
      } else if (outputFormat.equalsIgnoreCase("oneline")) {
        bwriter.write(annotator.tokenizeToText());
      } else {
        final KAFDocument.LinguisticProcessor newLp = kaf
            .addLinguisticProcessor("text", "ixa-pipe-tok-" + lang, version
                + "-" + commit);
        newLp.setBeginTimestamp();
        annotator.tokenizeToKAF(kaf);
        newLp.setEndTimestamp();
        bwriter.write(kaf.toString());
      }
      breader.close();
    }
    bwriter.close();
  }

  private void loadAnnotateParameters() {
    // specify language (for language dependent treatment of apostrophes)
    annotateParser
        .addArgument("-l", "--lang")
        .choices("de", "en", "es", "eu", "fr", "gl", "it", "nl")
        .required(true)
        .help(
            "It is REQUIRED to choose a language to perform annotation with ixa-pipe-tok.\n");
    annotateParser
        .addArgument("-n", "--normalize")
        .choices("alpino", "ancora", "ctag", "default", "ptb", "tiger",
            "tutpenn")
        .required(false)
        .setDefault("default")
        .help(
            "Set normalization method according to corpus; the default option does not escape "
                + "brackets or forward slashes. See README for more details.\n");
    annotateParser
        .addArgument("-u","--untokenizable")
        .choices("yes", "no")
        .setDefault("no")
        .required(false)
        .help("Print untokenizable characters.\n");
    annotateParser
        .addArgument("-o", "--outputFormat")
        .choices("conll", "oneline", "naf")
        .setDefault("naf")
        .required(false)
        .help(
            "Choose output format; it defaults to NAF.\n");
    annotateParser
        .addArgument("--offsets")
        .action(Arguments.storeFalse())
        .help(
            "Do not print offset and lenght information of tokens in CoNLL format.\n");
    annotateParser
        .addArgument("--inputkaf")
        .action(Arguments.storeTrue())
        .help(
            "Use this option if input is a KAF/NAF document with <raw> layer.\n");
    annotateParser
        .addArgument("--notok")
        .action(Arguments.storeTrue())
        .help(
            "Build a KAF document from an already tokenized sentence per line file.\n");
    annotateParser
        .addArgument("--hardParagraph")
        .choices("yes", "no")
        .setDefault("no")
        .required(false)
        .help("Do not segment paragraphs. Ever.\n");
    annotateParser.addArgument("--kafversion")
         .setDefault("v1.naf")
        .help("Set kaf document version.\n");
    annotateParser
    .addArgument("-server", "--server")
    .action(Arguments.storeTrue())
    .help(
      "Choose if you want to run the tokenizer as a server");
  }

  private Properties setAnnotateProperties(final String lang, final String normalize, final String untokenizable, final String hardParagraph) {
    final Properties annotateProperties = new Properties();
    annotateProperties.setProperty("language", lang);
    annotateProperties.setProperty("normalize", normalize);
    annotateProperties.setProperty("untokenizable", untokenizable);
    annotateProperties.setProperty("hardParagraph", hardParagraph);
    return annotateProperties;
  }

}
