package core.imagerenderer.util.pie;

import core.commands.utils.CommandUtil;
import core.parsers.params.CommandParameters;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.PieSeries;
import org.knowm.xchart.style.PieStyler;
import org.knowm.xchart.style.Styler;

import java.awt.*;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;


public interface IPieable<T, Y extends CommandParameters> {


    default CustomPieChart buildPie() {
        PieColourer randomPalette;
        if (CommandUtil.rand.nextBoolean()) {
            randomPalette = new MonochromeColourer();
        } else {
            randomPalette = new RandomPalette();
        }
        CustomPieChart pieChart = new CustomPieChart(
                new PieChartBuilder()
                        .width(1000)
                        .height(750).theme(Styler.ChartTheme.GGPlot2)
                , randomPalette
        );


        Color better = randomPalette.getAnnotationColour();
        PieStyler styler = pieChart.getStyler();

        styler.setLabelsDistance(1.15);

        styler.setCircular(true)
                .setLabelType(PieStyler.LabelType.NameAndPercentage)
                .setLabelsFontColorAutomaticEnabled(false)
                .setLabelsFontColor(better)
                .setForceAllLabelsVisible(true)
                .setClockwiseDirectionType(CommandUtil.rand.nextBoolean() ? PieStyler.ClockwiseDirectionType.CLOCKWISE : PieStyler.ClockwiseDirectionType.COUNTER_CLOCKWISE)
                .setStartAngleInDegrees(90)
                .setStartAngleInDegrees(CommandUtil.rand.nextInt(360));


        styler.setLegendVisible(false)
                .setPlotContentSize(0.80)
                .setChartFontColor(better)
                .setPlotBackgroundColor(randomPalette.getBackGroundColor())
                .setPlotBorderVisible(false)
                .setChartTitleBoxBackgroundColor(randomPalette.getTitleColour())
                .setChartBackgroundColor(randomPalette.getBesselColour());

        Color[] series = randomPalette.setPieSeriesColour();
        randomPalette.configChart(pieChart);
        if (series != null) {
            styler.setSeriesColors(series);
        }


        return pieChart;
    }


    PieChart fillPie(PieChart chart, Y params, T data);


    void fillSeries(PieChart pieChart, Function<T, String> keyMapping, ToIntFunction<T> valueMapping, Predicate<T> partitioner, T data);

    default PieChart doPie(Y params, T data) {
        CustomPieChart pieChart = buildPie();
        fillPie(pieChart, params, data);
        int counter = 0;
        for (PieSeries value : pieChart.getSeriesMap().values()) {
            Color color = pieChart.getColourer().setIndividualColour(counter++, pieChart.getSeriesMap().size());
            if (color != null) {
                value.setFillColor(color);
            }
        }
        return pieChart;
    }


    Map<Boolean, Map<String, Integer>> getData(T data, Function<T, String> keyMapping, ToIntFunction<T> valueMapping, Predicate<T> partitioner);

}
