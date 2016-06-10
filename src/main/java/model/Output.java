package model;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import object.VariantGeneScore;
import util.DBManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import object.EnsgGene;

/**
 *
 * @author Nick
 */
public class Output {

    public static ArrayList<VariantGeneScore> variantGeneScoreList = new ArrayList<>();
    public static boolean isRegionValid; // only check for out of bound , max 100kb
    public static final int maxVariantNumToDisplay = 5000;
    public static final int maxBaseNumToDisplay = 10000;
    
    public static void init() throws Exception {
        variantGeneScoreList.clear();
        isRegionValid = true;

        if (Upload.isUpload) {
            initVariantListByVariantFile();
        } else {
            if (Input.query.split("-").length == 4) { // search by variant id
                initVariantListByVariantId(Input.query);
            } else if (Input.query.contains(":")) { // search by region
                initVariantListByRegion(Input.query);
            } else if (Input.query.split("-").length == 2) { // search by variant site
                initVariantListByVariantSite(Input.query);
            } else if (Input.query.startsWith("ENSG")) { // search by ENSG gene
                initVariantListByEnsgGene(Input.query);
            } else { // search by HGNC gene or return nothing found
                initVariantListByHgncGene(Input.query);
            }
        }
    }

    private static void initVariantListByVariantFile() throws Exception {
        if (Upload.uploadErrMsg != null) {
            return;
        }

        File f = new File(Upload.filePath);
        FileInputStream fstream = new FileInputStream(f);
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        String lineStr = "";
        while ((lineStr = br.readLine()) != null) {
            if (!lineStr.isEmpty()) {
                lineStr = lineStr.replaceAll("( )+", "");

                if (lineStr.contains("-")) {
                    if (lineStr.split("-").length == 2) {
                        initVariantListByVariantSite(lineStr);
                    } else if (lineStr.split("-").length == 4) {
                        initVariantListByVariantId(lineStr);
                    }
                } else {
                    Upload.uploadErrMsg = "Wrong input values in your variant file: " + lineStr;
                    f.delete();
                    return;
                }
            }
        }

        br.close();
        in.close();
        fstream.close();

        f.delete();
    }

    public static void initVariantListByVariantSite(String site) throws Exception {
        String[] tmp = site.split("-"); // chr-pos

        String chr = tmp[0];
        int pos = Integer.valueOf(tmp[1]);

        String sql = "SELECT v.ref,v.alt,v.ensg_gene,g.hgnc_gene,v.score "
                + "FROM snv_score_chr" + chr + " v , ensg_hgnc_gene g "
                + "WHERE v.pos = " + pos + " "
                + "AND v.ensg_gene = g.ensg_gene";

        ResultSet rset = DBManager.executeQuery(sql);

        while (rset.next()) {
            VariantGeneScore variantGeneScore = new VariantGeneScore(
                    chr,
                    pos,
                    rset.getString("ref"),
                    rset.getString("alt"),
                    rset.getString("ensg_gene"),
                    rset.getString("hgnc_gene"),
                    rset.getFloat("score"));

            variantGeneScoreList.add(variantGeneScore);
        }

        rset.close();
    }

    public static void initVariantListByVariantId(String id) throws Exception {
        String[] tmp = id.split("-"); // chr-pos-ref-alt

        String chr = tmp[0];
        int pos = Integer.valueOf(tmp[1]);
        String ref = tmp[2];
        String alt = tmp[3];

        String sql = "SELECT v.ensg_gene,g.hgnc_gene,v.score "
                + "FROM snv_score_chr" + chr + " v , ensg_hgnc_gene g "
                + "WHERE v.pos = " + pos + " "
                + "AND v.alt='" + alt + "' "
                + "AND v.ensg_gene = g.ensg_gene";

        ResultSet rset = DBManager.executeQuery(sql);

        while (rset.next()) {
            VariantGeneScore variantGeneScore = new VariantGeneScore(
                    chr,
                    pos,
                    ref,
                    alt,
                    rset.getString("ensg_gene"),
                    rset.getString("hgnc_gene"),
                    rset.getFloat("score"));

            variantGeneScoreList.add(variantGeneScore);
        }

        rset.close();
    }

    public static void initVariantListByRegion(String region) throws Exception {
        String[] tmp = region.split(":"); // chr:start-end

        String chr = tmp[0].toLowerCase();

        if (chr.startsWith("chr")) {
            chr = chr.substring(3);
        }

        tmp = tmp[1].split("-");
        int start = Integer.valueOf(tmp[0]);
        int end = Integer.valueOf(tmp[1]);

        isRegionValid = isRegionValid(start, end);

        if (isRegionValid) {
            String sql = "SELECT v.pos,v.ref,v.alt,v.ensg_gene,g.hgnc_gene,v.score "
                    + "FROM snv_score_chr" + chr + " v , ensg_hgnc_gene g "
                    + "WHERE v.pos BETWEEN " + start + " AND " + end + " "
                    + "AND v.ensg_gene = g.ensg_gene";

            ResultSet rset = DBManager.executeQuery(sql);

            while (rset.next()) {
                VariantGeneScore variantGeneScore
                        = new VariantGeneScore(chr,
                                rset.getInt("pos"),
                                rset.getString("ref"),
                                rset.getString("alt"),
                                rset.getString("ensg_gene"),
                                rset.getString("hgnc_gene"),
                                rset.getFloat("score"));

                variantGeneScoreList.add(variantGeneScore);
            }

            rset.close();
        }
    }

    private static boolean isRegionValid(int start, int end) {
        return end - start <= maxBaseNumToDisplay;
    }

    public static void initVariantListByEnsgGene(String ensg) throws Exception {
        EnsgGene ensgGene = getEnsgGene(ensg);

        if (ensgGene == null) { // not a valid ensg gene
            return;
        }

        String sql = "SELECT v.pos,v.ref,v.alt,v.ensg_gene,g.hgnc_gene,v.score "
                + "FROM snv_score_chr" + ensgGene.getChr() + " v , ensg_hgnc_gene g "
                + "WHERE v.pos BETWEEN " + ensgGene.getStart() + " AND " + ensgGene.getEnd() + " "
                + "AND v.ensg_gene ='" + ensgGene.getName() + "' "
                + "AND v.ensg_gene = g.ensg_gene";

        ResultSet rset = DBManager.executeQuery(sql);

        while (rset.next()) {
            VariantGeneScore variantGeneScore
                    = new VariantGeneScore(
                            ensgGene.getChr(),
                            rset.getInt("pos"),
                            rset.getString("ref"),
                            rset.getString("alt"),
                            rset.getString("ensg_gene"),
                            rset.getString("hgnc_gene"),
                            rset.getFloat("score"));

            variantGeneScoreList.add(variantGeneScore);
        }

        rset.close();
    }

    private static EnsgGene getEnsgGene(String ensg) throws Exception {
        String sql = "SELECT * "
                + "FROM ensg_gene_region "
                + "WHERE ensg_gene = '" + ensg + "'";

        ResultSet rset = DBManager.executeQuery(sql);

        EnsgGene ensgGene = null;

        if (rset.next()) {
            ensgGene = new EnsgGene(
                    ensg,
                    rset.getString("chr"),
                    rset.getInt("start"),
                    rset.getInt("end"));
        }

        rset.close();

        return ensgGene;
    }

    public static void initVariantListByHgncGene(String hgnc) throws Exception {
        String ensg = getEnsgGeneNameByHgnc(hgnc);

        initVariantListByEnsgGene(ensg);
    }

    private static String getEnsgGeneNameByHgnc(String hgnc) throws Exception {
        String sql = "SELECT ensg_gene "
                + "FROM ensg_hgnc_gene "
                + "WHERE hgnc_gene = '" + hgnc + "'";

        ResultSet rset = DBManager.executeQuery(sql);

        if (rset.next()) {
            return rset.getString("ensg_gene");
        }

        rset.close();

        return "NA";
    }
}
